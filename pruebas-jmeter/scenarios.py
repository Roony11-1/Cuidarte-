import json
from pathlib import Path

from jmx_builder import JmxPlanBuilder
from openapi_discovery import ApiPrefixes
from rut_generator import ChileanRutGenerator


class LightPrScenario:
    """Escenario de carga ligera para Pull Requests (20 solicitudes GET consecutivas)."""

    def __init__(self, prefixes: ApiPrefixes) -> None:
        self.prefixes = prefixes

    def build_to_file(self, output_path: str | Path) -> Path:
        builder = JmxPlanBuilder(
            plan_name="Cuidarte+ JMeter PR Light",
            serialize_thread_groups=True,
        )
        self._add_setup_group(builder)
        self._add_load_group(builder)
        self._add_teardown_group(builder)
        return builder.save(output_path)

    def _add_setup_group(self, builder: JmxPlanBuilder) -> None:
        setup_ht = builder.add_thread_group(
            name="00_Setup_Auth_And_Isolated_Data",
            tg_type="setup",
            threads=1,
            loops=1,
        )
        builder.add_header_manager(setup_ht, "JSON Header", {"Content-Type": "application/json"})

        login_admin = builder.add_http_sampler(
            setup_ht,
            name="Setup_Login_Admin",
            method="POST",
            path=f"{self.prefixes.auth}/login",
            body='{"nombre_usuario":"${__P(admin_user)}","password":"${__P(admin_pass)}"}',
        )
        builder.add_status_assertion(login_admin, 200)
        builder.add_json_extractor(login_admin, "Extract Admin Token", "token_admin", "$.token")
        builder.add_groovy_postprocessor(
            login_admin, "Store Admin JWT", 'props.put("jwt_admin", vars.get("token_admin"))'
        )

        login_medico = builder.add_http_sampler(
            setup_ht,
            name="Setup_Login_Medico",
            method="POST",
            path=f"{self.prefixes.auth}/login",
            body='{"nombre_usuario":"${__P(medico_user)}","password":"${__P(medico_pass)}"}',
        )
        builder.add_status_assertion(login_medico, 200)
        builder.add_json_extractor(login_medico, "Extract Medico Token", "token_medico", "$.token")
        builder.add_groovy_postprocessor(
            login_medico, "Store Medico JWT", 'props.put("jwt_medico", vars.get("token_medico"))'
        )

        unique_num, rut_test = ChileanRutGenerator.random_test_rut()
        paciente_payload = json.dumps(
            {
                "rut": rut_test,
                "primer_nombre": "JMeterPR",
                "apellido_paterno": "Aislado",
                "apellido_materno": "CI",
                "fecha_nacimiento": "1994-08-20",
                "sexo": "M",
                "email": f"perf.pr.{unique_num}@cuidarteplus.test",
            }
        )
        create_paciente = builder.add_http_sampler(
            setup_ht,
            name="Setup_Create_Isolated_Paciente",
            method="POST",
            path=f"{self.prefixes.pacientes}/",
            body=paciente_payload,
        )
        builder.add_header_manager(
            create_paciente,
            "Auth Medico JSON",
            {
                "Content-Type": "application/json",
                "Authorization": "Bearer ${__P(jwt_medico)}",
            },
        )
        builder.add_status_assertion(create_paciente, 201)
        builder.add_json_extractor(create_paciente, "Extract Paciente ID", "paciente_test_id", "$.id")
        builder.add_groovy_postprocessor(
            create_paciente,
            "Store Paciente ID",
            'props.put("paciente_test_id", vars.get("paciente_test_id"))',
        )

    def _add_load_group(self, builder: JmxPlanBuilder) -> None:
        load_ht = builder.add_thread_group(
            name="01_Load_Light_20_Consecutive_GET",
            tg_type="normal",
            threads=1,
            loops=10,
        )
        builder.add_header_manager(
            load_ht,
            "Auth Medico Load",
            {"Authorization": "Bearer ${__P(jwt_medico)}"},
        )

        get_tipos = builder.add_http_sampler(
            load_ht,
            name="PERF_GET_TipoExamen_List",
            method="GET",
            path=f"{self.prefixes.tipo_examen}/",
        )
        builder.add_status_assertion(get_tipos, 200)

        get_paciente = builder.add_http_sampler(
            load_ht,
            name="PERF_GET_Paciente_ById",
            method="GET",
            path=f"{self.prefixes.pacientes}/${{__P(paciente_test_id)}}",
        )
        builder.add_status_assertion(get_paciente, 200)

    def _add_teardown_group(self, builder: JmxPlanBuilder) -> None:
        td_ht = builder.add_thread_group(
            name="99_TearDown_Cleanup_Isolated_Data",
            tg_type="teardown",
            threads=1,
            loops=1,
        )
        builder.add_header_manager(
            td_ht,
            "Auth Admin Cleanup",
            {"Authorization": "Bearer ${__P(jwt_admin)}"},
        )

        delete_paciente = builder.add_http_sampler(
            td_ht,
            name="TearDown_Delete_Paciente",
            method="DELETE",
            path=f"{self.prefixes.pacientes}/${{__P(paciente_test_id)}}",
        )
        builder.add_status_assertion(delete_paciente, 200)
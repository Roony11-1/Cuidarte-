import json
import urllib.request
from dataclasses import dataclass


@dataclass(frozen=True)
class ApiPrefixes:
    auth: str
    pacientes: str
    tipo_examen: str

    def to_dict(self) -> dict[str, str]:
        return {
            "auth": self.auth,
            "pacientes": self.pacientes,
            "tipo_examen": self.tipo_examen,
        }


class OpenApiRouteResolver:
    """Resuelve dinámicamente los prefijos de rutas REST consultando /openapi.json."""

    def __init__(self, openapi_url: str, timeout: int = 15) -> None:
        self.openapi_url = openapi_url
        self.timeout = timeout

    def resolve(self) -> ApiPrefixes:
        with urllib.request.urlopen(self.openapi_url, timeout=self.timeout) as response:
            spec = json.loads(response.read().decode("utf-8"))

        paths = list(spec.get("paths", {}).keys())
        auth_prefix = self._match_prefix(paths, suffix="/login", keyword="auth")
        pacientes_prefix = self._match_prefix(paths, suffix="/buscar/rut/{rut}", keyword="paciente")
        tipo_examen_prefix = self._find_collection_path(paths, keywords=("tipo", "examen"))

        missing = [
            name
            for name, value in (
                ("auth", auth_prefix),
                ("pacientes", pacientes_prefix),
                ("tipo_examen", tipo_examen_prefix),
            )
            if not value
        ]
        if missing:
            raise RuntimeError(
                f"No se pudieron resolver prefijos OpenAPI para {missing}. Rutas disponibles: {paths}"
            )

        return ApiPrefixes(
            auth=auth_prefix,
            pacientes=pacientes_prefix,
            tipo_examen=tipo_examen_prefix,
        )

    @staticmethod
    def _match_prefix(paths: list[str], suffix: str, keyword: str) -> str:
        for path in paths:
            if path.endswith(suffix) and keyword in path:
                return path[: -len(suffix)] if suffix else path
        return ""

    @staticmethod
    def _find_collection_path(paths: list[str], keywords: tuple[str, ...]) -> str:
        for path in paths:
            if all(kw in path for kw in keywords) and "{" not in path:
                return path.rstrip("/")
        return ""
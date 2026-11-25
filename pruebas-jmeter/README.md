# Pruebas de Rendimiento Automatizadas — Cuidarte+ (Apache JMeter)

Este directorio contiene la suite modular de pruebas de rendimiento automatizadas para la API REST de **Cuidarte+**. Su objetivo es validar tiempos de respuesta, estabilidad HTTP y ausencia de regresiones de latencia en cada *Pull Request* hacia `main`, ejecutando **Apache JMeter 5.6.3** dentro de Docker sin necesidad de instalar JMeter localmente ni mantener archivos `.jmx` estáticos en el repositorio.

---

## 1. Arquitectura y Estructura de Archivos

La suite está desarrollada en **Python 3.12** utilizando exclusivamente su librería estándar (`xml.etree.ElementTree`, `urllib.request`, `csv`, `json`, `math`) y se divide en módulos desacoplados:

```text
pruebas-jmeter/
├── __init__.py              # Inicialización del paquete
├── openapi_discovery.py     # Resolución dinámica de prefijos de rutas desde /openapi.json
├── rut_generator.py         # Generador de RUTs chilenos válidos (algoritmo Módulo 11)
├── jmx_builder.py           # Constructor XML fluido para planes de prueba JMeter 5.6.3
├── scenarios.py             # Definición del escenario de carga ligera para Pull Requests
├── result_validator.py      # Analizador estadístico de resultados .jtl y verificación de SLAs
├── generate_plan.py         # Script CLI para generar el archivo .jmx antes de la ejecución
├── validate_report.py       # Script CLI para auditar métricas y bloquear el CI si falla un umbral
└── output/                  # Carpeta temporal de artefactos (ignorada en Git)
```

---

## 2. Flujo del Plan de Pruebas (`LightPrScenario`)

El plan generado (`cuidarteplus-pr.jmx`) configura `TestPlan.serialize_threadgroups = true` para garantizar que la preparación de datos, la medición de carga y la limpieza se ejecuten de forma estrictamente secuencial:

### Fase 0: `00_Setup_Auth_And_Isolated_Data` (`SetupThreadGroup`)
1. **Autenticación (`POST /autenticacion/login`):** Inicia sesión con los usuarios del entorno de pruebas (`admin` y `medico`), extrae los tokens JWT (`$.token`) y los almacena como propiedades globales de JMeter (`jwt_admin` y `jwt_medico`).
2. **Creación de paciente aislado (`POST /pacientes/`):** Genera un RUT válido aleatorio y registra un paciente temporal de prueba, guardando su `id` (`paciente_test_id`) para evitar dependencias con datos preexistentes.

### Fase 1: `01_Load_Light_20_Consecutive_GET` (`ThreadGroup`)
* **Configuración:** `1` hilo virtual, `10` iteraciones consecutivas sobre dos operaciones de lectura (`20` solicitudes HTTP `GET` en total) con `KeepAlive` habilitado y sin reintentos ocultos (`httpclient4.retrycount=0`).
* **Endpoints evaluados:**
  * `GET /tipo-examen/` (`PERF_GET_TipoExamen_List`)
  * `GET /pacientes/${__P(paciente_test_id)}` (`PERF_GET_Paciente_ById`)
* *Nota técnica:* Se seleccionan endpoints de lectura directa que no poseen retardos artificiales de demostración (`delayMiddleware`), permitiendo medir con precisión el rendimiento real de Express y PostgreSQL.

### Fase 2: `99_TearDown_Cleanup_Isolated_Data` (`PostThreadGroup`)
* **Limpieza (`DELETE /pacientes/${__P(paciente_test_id)}`):** Elimina el registro de prueba creado en la Fase 0 utilizando el token de `admin`, dejando la base de datos limpia al finalizar.

---

## 3. Criterios de Aceptación y Umbrales (SLA)

El validador (`result_validator.py`) filtra exclusivamente las muestras etiquetadas con el prefijo `PERF_*` (excluyendo `Setup_*` y `TearDown_*` para no distorsionar los percentiles) y exige el cumplimiento simultáneo de los siguientes criterios:

| Métrica | Criterio Exigido | Descripción |
| :--- | :--- | :--- |
| **Total de solicitudes** | `== 20` | Ejecución completa de las 20 peticiones `GET` consecutivas. |
| **Latencia Percentil 95 (`p95`)** | `< 300 ms` | El 95% de las respuestas debe completarse por debajo de 300 milisegundos. |
| **Errores HTTP `5xx`** | `== 0` | Cero fallos internos del servidor o errores de conexión a base de datos. |
| **Solicitudes fallidas** | `== 0` | Todas las aserciones de código HTTP (`200 OK`) deben resultar exitosas. |

---

## 4. Integración en CI/CD (GitHub Actions)

* **Orquestación:** El workflow principal (`.github/workflows/ci-pipeline.yml`) invoca al workflow reutilizable `.github/workflows/jmeter-pr.yml` (`workflow_call`) en cada *Pull Request* hacia `main` cuando se modifican archivos en `BACKEND/**`, `pruebas-jmeter/**`, `docker-compose.yml` o los propios workflows, siempre que `sonar-backend` y `selenium` hayan finalizado correctamente (o hayan sido omitidos).
* **Ejecución en red interna Docker:** JMeter corre mediante la imagen oficial `alpine/jmeter:5.6.3` conectada directamente a la red `cuidarteplus-network`, apuntando al contenedor `http://cuidarteplus-backend-ev:4000`.
* **Seguridad de credenciales y logs:** Se desactiva el guardado de cabeceras en el archivo `.jtl` (`jmeter.save.saveservice.requestHeaders=false` y `responseHeaders=false`) para impedir que los encabezados `Authorization: Bearer <JWT>` queden expuestos en los *artifacts* descargables.
* **Artifacts publicados:**
  * `results.jtl`: Registro tabular bruto de cada muestra ejecutada.
  * `html-report/`: Dashboard gráfico interactivo generado por Apache JMeter.
  * `performance-summary.json`: Resumen estructurado con promedio, mediana (`p50`), `p95` y estado de aprobación.

---

## 5. Ejecución Local (Opcional)

Si deseas ejecutar la suite en tu máquina local teniendo Docker instalado:

1. **Levantar el backend y la base de datos:**
   ```bash
   PORT_BACKEND=4445 PORT_POSTGRES=15443 docker compose up -d --build cuidarteplus-backend-ev
   ```

2. **Generar el plan dinámico `.jmx`:**
   ```bash
   mkdir -p pruebas-jmeter/output
   python3 pruebas-jmeter/generate_plan.py \
     --openapi-url http://localhost:4445/openapi.json \
     --output pruebas-jmeter/output/cuidarteplus-pr.jmx
   ```

3. **Ejecutar JMeter mediante Docker:**
   ```bash
   docker run --rm \
     --network cuidarteplus-network \
     -v "$(pwd)/pruebas-jmeter/output:/results" \
     --entrypoint jmeter \
     alpine/jmeter:5.6.3 \
     -n \
     -t /results/cuidarteplus-pr.jmx \
     -l /results/results.jtl \
     -e -o /results/html-report \
     -Japi_protocol=http \
     -Japi_host=cuidarteplus-backend-ev \
     -Japi_port=4000 \
     -Jadmin_user=admin \
     -Jadmin_pass=admin \
     -Jmedico_user=medico \
     -Jmedico_pass=medico \
     -Jhttpclient4.retrycount=0 \
     -Jjmeter.save.saveservice.requestHeaders=false \
     -Jjmeter.save.saveservice.responseHeaders=false
   ```

4. **Validar resultados y limpiar entorno:**
   ```bash
   python3 pruebas-jmeter/validate_report.py \
     --jtl pruebas-jmeter/output/results.jtl \
     --summary pruebas-jmeter/output/performance-summary.json

   docker compose down -v
   ```
import argparse
import json
import os
from pathlib import Path

from openapi_discovery import OpenApiRouteResolver
from scenarios import LightPrScenario


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Genera el plan .jmx dinámico para JMeter.")
    parser.add_argument(
        "--openapi-url",
        default=os.environ.get("HOST_OPENAPI_URL", "http://localhost:4445/openapi.json"),
        help="URL del endpoint openapi.json del backend activo.",
    )
    parser.add_argument(
        "--output",
        default="pruebas-jmeter/output/cuidarteplus-pr.jmx",
        help="Ruta de salida del archivo .jmx generado.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    resolver = OpenApiRouteResolver(openapi_url=args.openapi_url)
    prefixes = resolver.resolve()

    scenario = LightPrScenario(prefixes=prefixes)
    generated_path = scenario.build_to_file(Path(args.output))

    print("Prefijos OpenAPI resueltos:", json.dumps(prefixes.to_dict()))
    print(f"Plan JMeter generado en: {generated_path}")


if __name__ == "__main__":
    main()
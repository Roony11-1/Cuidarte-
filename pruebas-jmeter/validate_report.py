import argparse
import os
import sys

from result_validator import JtlResultValidator, PerformanceThresholds


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Valida resultados .jtl de JMeter contra SLAs.")
    parser.add_argument(
        "--jtl",
        default="pruebas-jmeter/output/results.jtl",
        help="Ruta al archivo results.jtl generado por JMeter.",
    )
    parser.add_argument(
        "--summary",
        default="pruebas-jmeter/output/performance-summary.json",
        help="Ruta de salida para el resumen JSON.",
    )
    parser.add_argument(
        "--expected-requests",
        type=int,
        default=20,
        help="Cantidad exacta de muestras PERF_* esperadas.",
    )
    parser.add_argument(
        "--max-p95-ms",
        type=float,
        default=300.0,
        help="Umbral máximo permitido para latencia p95 en milisegundos.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    thresholds = PerformanceThresholds(
        expected_requests=args.expected_requests,
        max_p95_ms=args.max_p95_ms,
    )
    validator = JtlResultValidator(thresholds=thresholds)

    passed, failures, summary = validator.validate(
        jtl_path=args.jtl,
        summary_output_path=args.summary,
        profile="light",
        source="pr-build",
        pr_number=os.environ.get("EVALUATED_PR", ""),
        commit_sha=os.environ.get("EVALUATED_SHA", ""),
    )

    if summary is not None:
        print(summary.to_json())

    if not passed:
        print("\nFALLOS EN VALIDACIÓN:")
        for msg in failures:
            print(" -", msg)
        return 1

    print("\nVALIDACIÓN PR LIGHT EXITOSA.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
import csv
import json
import math
from dataclasses import asdict, dataclass
from pathlib import Path


@dataclass(frozen=True)
class PerformanceThresholds:
    expected_requests: int = 20
    max_p95_ms: float = 300.0
    max_failed_requests: int = 0
    max_http_5xx: int = 0


@dataclass
class ValidationSummary:
    profile: str
    source: str
    pr_number: str
    commit_sha: str
    total_requests: int
    failed_requests: int
    http_5xx_count: int
    latency_avg_ms: float
    latency_p50_ms: float
    latency_p95_ms: float
    passed: bool

    def to_json(self) -> str:
        return json.dumps(asdict(self), indent=2)


class JtlResultValidator:
    """Analiza archivos .jtl de JMeter y verifica cumplimiento de SLA y errores HTTP."""

    def __init__(self, thresholds: PerformanceThresholds | None = None) -> None:
        self.thresholds = thresholds or PerformanceThresholds()

    def validate(
        self,
        jtl_path: str | Path,
        summary_output_path: str | Path,
        profile: str = "light",
        source: str = "pr-build",
        pr_number: str = "",
        commit_sha: str = "",
    ) -> tuple[bool, list[str], ValidationSummary | None]:
        jtl_file = Path(jtl_path)
        if not jtl_file.exists():
            return False, [f"No se generó el archivo de resultados: {jtl_file}"], None

        with jtl_file.open("r", encoding="utf-8") as handle:
            rows = list(csv.DictReader(handle))

        if not rows:
            return False, ["El archivo results.jtl está vacío."], None

        failures: list[str] = []
        for row in rows:
            label = row.get("label", "")
            if label.startswith(("Setup_", "TearDown_")) and row.get("success", "").lower() != "true":
                failures.append(
                    f"[{label}] HTTP {row.get('responseCode')}: {row.get('failureMessage')}"
                )

        perf_rows = [row for row in rows if row.get("label", "").startswith("PERF_")]
        if not perf_rows:
            failures.append("No se registraron muestras de rendimiento (PERF_*).")
            return False, failures, None

        latencies = [float(row["elapsed"]) for row in perf_rows]
        errors = [row for row in perf_rows if row.get("success", "").lower() != "true"]
        err_5xx = [
            row
            for row in perf_rows
            if row.get("responseCode", "").isdigit() and 500 <= int(row["responseCode"]) < 600
        ]

        avg_ms = sum(latencies) / len(latencies)
        p50_ms = self._percentile(latencies, 50.0)
        p95_ms = self._percentile(latencies, 95.0)

        if len(perf_rows) != self.thresholds.expected_requests:
            failures.append(
                f"Se esperaban {self.thresholds.expected_requests} solicitudes, se registraron {len(perf_rows)}."
            )
        if len(err_5xx) > self.thresholds.max_http_5xx:
            failures.append(f"Se detectaron {len(err_5xx)} errores HTTP 5xx.")
        if len(errors) > self.thresholds.max_failed_requests:
            failures.append(f"Se detectaron {len(errors)} solicitudes fallidas.")
        if p95_ms >= self.thresholds.max_p95_ms:
            failures.append(
                f"Latencia p95 ({p95_ms:.2f} ms) supera el umbral de {self.thresholds.max_p95_ms:.0f} ms."
            )

        summary = ValidationSummary(
            profile=profile,
            source=source,
            pr_number=pr_number,
            commit_sha=commit_sha,
            total_requests=len(perf_rows),
            failed_requests=len(errors),
            http_5xx_count=len(err_5xx),
            latency_avg_ms=round(avg_ms, 2),
            latency_p50_ms=round(p50_ms, 2),
            latency_p95_ms=round(p95_ms, 2),
            passed=len(failures) == 0,
        )

        out_file = Path(summary_output_path)
        out_file.parent.mkdir(parents=True, exist_ok=True)
        out_file.write_text(summary.to_json(), encoding="utf-8")

        return summary.passed, failures, summary

    @staticmethod
    def _percentile(values: list[float], pct: float) -> float:
        ordered = sorted(values)
        k = (len(ordered) - 1) * (pct / 100.0)
        lower = math.floor(k)
        upper = math.ceil(k)
        if lower == upper:
            return ordered[int(k)]
        return ordered[lower] * (upper - k) + ordered[upper] * (k - lower)
"""
Script de prueba de carga local para LogiRed (Eje de Rendimiento: p95 < 500 ms).
Ejecuta solicitudes concurrentes contra la API REST autenticada y calcula
estadísticas de latencia: promedio, mediana, p90, p95 y p99.
"""

import sys
import time
import base64
import urllib.request
import urllib.error
from concurrent.futures import ThreadPoolExecutor
import statistics

BASE_URL = "http://localhost:8080/api/productos"
USER = "operador"
PASSWORD = "operadorPassword123!"  # o valor definido en .env / application.properties
TOTAL_REQUESTS = 100
CONCURRENCY = 5

def create_request(url, username, password):
    req = urllib.request.Request(url)
    auth_str = f"{username}:{password}".encode("utf-8")
    req.add_header("Authorization", f"Basic {base64.b64encode(auth_str).decode('ascii')}")
    req.add_header("Accept", "application/json")
    return req

def single_request(idx):
    req = create_request(BASE_URL, USER, PASSWORD)
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            resp.read()
            duration_ms = (time.perf_counter() - start) * 1000
            return True, duration_ms, resp.status
    except urllib.error.HTTPError as e:
        duration_ms = (time.perf_counter() - start) * 1000
        return False, duration_ms, e.code
    except Exception as e:
        duration_ms = (time.perf_counter() - start) * 1000
        return False, duration_ms, str(e)

def run_load_test():
    print("=" * 60)
    print(f"Iniciando prueba de carga LogiRed: {TOTAL_REQUESTS} peticiones (concurrencia {CONCURRENCY})")
    print(f"Target: {BASE_URL}")
    print("=" * 60)

    durations = []
    successes = 0
    failures = 0

    t_start = time.perf_counter()
    with ThreadPoolExecutor(max_workers=CONCURRENCY) as executor:
        results = list(executor.map(single_request, range(TOTAL_REQUESTS)))
    total_time = time.perf_counter() - t_start

    for ok, dur, status in results:
        durations.append(dur)
        if ok or status in (200, 201, 204):
            successes += 1
        else:
            failures += 1

    durations.sort()
    avg_lat = statistics.mean(durations)
    median_lat = statistics.median(durations)
    p90 = durations[int(len(durations) * 0.90)]
    p95 = durations[int(len(durations) * 0.95)]
    p99 = durations[int(len(durations) * 0.99)]
    throughput = TOTAL_REQUESTS / total_time

    print(f"\nResultados:")
    print(f"- Exitosas: {successes} | Fallidas: {failures}")
    print(f"- Tiempo total: {total_time:.2f} s ({throughput:.2f} req/s)")
    print(f"- Latencia Promedio: {avg_lat:.2f} ms")
    print(f"- Latencia Mediana:  {median_lat:.2f} ms")
    print(f"- Latencia p90:      {p90:.2f} ms")
    print(f"- Latencia p95:      {p95:.2f} ms  <-- [Umbral cátedra < 500 ms]")
    print(f"- Latencia p99:      {p99:.2f} ms")

    if p95 < 500:
        print("\n✅ CRITERIO CUMPLIDO: p95 es MENOR a 500 ms")
    else:
        print("\n❌ CRITERIO NO CUMPLIDO: p95 superó los 500 ms")
    print("=" * 60)

if __name__ == "__main__":
    run_load_test()

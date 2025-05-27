import os
import glob
import re
import subprocess
import sys
import time

GREEN = "\033[32m"
RED = "\033[31m"
YELLOW = "\033[33m"
ORANGE = "\033[38;5;208m"
CYAN = "\033[36m"
RESET = "\033[0m"

total_tests = 0
passed_tests = 0
failed_tests = 0

sources_dir = os.path.join("simple", "simple_sources")
tests_dir = os.path.join("simple", "tests")
test_files = glob.glob(os.path.join(tests_dir, "test_*_source*.txt"))
test_files.sort()

for test_file in test_files:
    file_failures = []
    m = re.search(r"test_(.+?)_source(\d+)\.txt", os.path.basename(test_file))
    if not m:
        continue
    testname = m.group(1)
    number = m.group(2)
    source_file = os.path.join(sources_dir, f"source{number}.txt")
    if not os.path.exists(source_file):
        print(f"{YELLOW}Source file {source_file} does not exist, skipping{RESET}")
        continue
    process = subprocess.Popen(
        ["java", "-jar", "target/TreeSitter-1.0-SNAPSHOT.jar", source_file],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        bufsize=1,
    )
    start = time.time()
    while True:
        line = process.stdout.readline()
        if not line:
            process.kill()
            sys.exit(1)
        if "Ready" in line:
            break
        if time.time() - start > 10:
            process.kill()
            sys.exit(1)
    with open(test_file, "r", encoding="ibm852") as f:
        lines = [l.rstrip("\n") for l in f]
    i = 0
    while i < len(lines):
        if i + 2 >= len(lines):
            file_failures.append((None, None, None, None))
            break
        declarations = lines[i]
        query = lines[i+1]
        expected = lines[i+2]
        i += 3
        total_tests += 1
        process.stdin.write(declarations + "\n")
        process.stdin.write(query + "\n")
        process.stdin.flush()
        answer = process.stdout.readline().rstrip("\n")
        exp_items = [s.strip() for s in expected.split(",")]
        ans_items = [s.strip() for s in answer.split(",")]
        try:
            exp_sorted = sorted(exp_items, key=lambda x: int(x) if x.isdigit() else x)
            ans_sorted = sorted(ans_items, key=lambda x: int(x) if x.isdigit() else x)
        except:
            exp_sorted = sorted(exp_items)
            ans_sorted = sorted(ans_items)
        if exp_sorted == ans_sorted:
            passed_tests += 1
        else:
            failed_tests += 1
            file_failures.append((declarations, query, exp_sorted, ans_sorted))
    print(f"{CYAN}{'='*20} {testname} {'='*20}{RESET}")
    if not file_failures:
        print(f"{GREEN}OK{RESET}")
    else:
        for decl, qry, exp_sorted, ans_sorted in file_failures:
            if decl is None:
                print(f"{RED}Test file format error{RESET}")
            else:
                print(f"{CYAN}{decl}{RESET}")
                print(f"{ORANGE}{qry}{RESET}")
                print(f"{YELLOW}expected\n{', '.join(exp_sorted)}{RESET}")
                print(f"{RED}got\n{', '.join(ans_sorted)}{RESET}")
    print()
process.terminate()
print(f"Przeszło: {passed_tests}")
print(f"Nie przeszło: {failed_tests}")
if failed_tests:
    sys.exit(1)
sys.exit(0)

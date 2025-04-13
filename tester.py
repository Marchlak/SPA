import os
import glob
import re
import subprocess
import sys
import time

directory = "simple"
source_files = glob.glob(os.path.join(directory, "source*.txt"))
source_files.sort()

for source_file in source_files:
    m = re.search(r"source(\d+)\.txt", os.path.basename(source_file))
    if not m:
        continue
    number = m.group(1)
    test_file = os.path.join(directory, f"test{number}.txt")
    if not os.path.exists(test_file):
        print(f"Test file {test_file} does not exist, skipping.")
        continue
    print(f"Testing with {source_file} and {test_file}")
    cmd = ["java", "-jar", "target/TreeSitter-1.0-SNAPSHOT.jar", source_file]
    process = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, bufsize=1)
    timeout = 10
    start = time.time()
    while True:
        line = process.stdout.readline()
        if not line:
            print("Preparation Crash")
            process.kill()
            sys.exit(1)
        if "Ready" in line:
            break
        if time.time() - start > timeout:
            print("PreparationTimeout")
            process.kill()
            sys.exit(1)
    with open(test_file, "r", encoding="ibm852") as f:
        lines = [l.rstrip("\n") for l in f]
    i = 0
    while i < len(lines):
        if i + 2 >= len(lines):
            print("Test file format error: expected three lines per test case")
            break
        declarations = lines[i]
        query = lines[i+1]
        expected = lines[i+2]
        i += 3
        process.stdin.write(declarations + "\n")
        process.stdin.write(query + "\n")
        process.stdin.flush()
        answer = process.stdout.readline().rstrip("\n")
        print(f"for query {query}:")
        exp_nums = re.findall(r'\d+', expected)
        ans_nums = re.findall(r'\d+', answer)
        if exp_nums and ans_nums:
            if sorted(exp_nums) == sorted(ans_nums):
                print("OK:", answer)
            else:
                print("Mismatch: expected:", expected, "got:", answer)
        else:
            if answer == expected:
                print("OK:", answer)
            else:
                print("Mismatch: expected:", expected, "got:", answer)

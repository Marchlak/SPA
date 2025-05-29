import sys
import os
import re

def number_statements(input_path):
    base, ext = os.path.splitext(input_path)
    output_path = f"{base}numbered{ext}"
    pattern = re.compile(r'^\s*(?:call\s+\w+\s*;|\w+\s*=.*;|while\s+\w+\s*{|if\s+\w+\s*then\s*{)')
    count = 0
    with open(input_path) as infile, open(output_path, "w") as outfile:
        for line in infile:
            if pattern.match(line):
                count += 1
                outfile.write(f"{count}. {line}")
            else:
                outfile.write(line)

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python3 number_lines.py <source_file.txt>")
    else:
        number_statements(sys.argv[1])


#!/usr/bin/env python3
import json
import sys
import os
import glob

def load_all_summaries(json_files):
    """Load summaries from all JSON files and group by test name"""
    summaries_by_name = {}

    for json_file in json_files:
        try:
            with open(json_file, 'r') as f:
                data = json.load(f)

            if 'summaries' in data and data['summaries']:
                for summary in data['summaries']:
                    name = summary.get('name', 'Unknown Test')
                    if name not in summaries_by_name:
                        summaries_by_name[name] = []
                    summaries_by_name[name].append(summary)
        except (FileNotFoundError, json.JSONDecodeError) as e:
            print(f"Warning: Error processing {json_file}: {e}", file=sys.stderr)
            continue

    return summaries_by_name

def convert_to_markdown(json_files):
    """Convert performance test JSON results to markdown format"""
    summaries_by_name = load_all_summaries(json_files)

    if not summaries_by_name:
        return "## Performance Test Results\n\nNo performance test results found."

    markdown = "## Performance Test Results\n\n"

    # Create a table for each test name
    for name, summaries in sorted(summaries_by_name.items()):
        markdown += f"### {name}\n\n"
        markdown += "| Duration (ms) | Max Memory (GB) | Processors | Parameters |\n"
        markdown += "|---------------|-----------------|------------|------------|\n"

        for summary in summaries:
            duration = summary.get('duration', 0)
            processors = summary.get('numberOfProcessors', 0)
            max_memory = summary.get('maxMemory', 0)

            # Convert memory from bytes to GB
            max_memory_gb = max_memory / (1024 ** 3) if max_memory > 0 else 0

            # Extract dynamic properties (excluding standard fields)
            standard_fields = {'name', 'duration', 'numberOfProcessors', 'maxMemory'}
            params = []
            for key, value in summary.items():
                if key not in standard_fields:
                    params.append(f"{key}={value}")

            params_str = ", ".join(params) if params else "-"

            markdown += f"| {duration} | {max_memory_gb:.2f} | {processors} | {params_str} |\n"

        markdown += "\n"

    return markdown

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: performance-to-markdown.py <json_file> [json_file2 ...]")
        print("   or: performance-to-markdown.py <glob_pattern>")
        sys.exit(1)

    # Collect all JSON files from arguments (supporting both direct files and glob patterns)
    json_files = []
    for arg in sys.argv[1:]:
        if '*' in arg:
            json_files.extend(glob.glob(arg, recursive=True))
        else:
            json_files.append(arg)

    # Filter to only existing files
    json_files = [f for f in json_files if os.path.isfile(f)]

    if not json_files:
        print("## Performance Test Results\n\nNo performance test results found.")
        sys.exit(0)

    markdown = convert_to_markdown(json_files)
    print(markdown)

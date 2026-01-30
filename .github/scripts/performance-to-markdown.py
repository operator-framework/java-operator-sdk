#!/usr/bin/env python3
import json
import sys
import os

def convert_to_markdown(json_file):
    """Convert performance test JSON results to markdown format"""
    try:
        with open(json_file, 'r') as f:
            data = json.load(f)
    except FileNotFoundError:
        return "## Performance Test Results\n\nNo performance test results found."
    except json.JSONDecodeError:
        return "## Performance Test Results\n\nError parsing performance test results."

    markdown = "## Performance Test Results\n\n"

    if 'summaries' not in data or not data['summaries']:
        return markdown + "No test summaries available."

    for summary in data['summaries']:
        name = summary.get('name', 'Unknown Test')
        duration = summary.get('duration', 0)
        processors = summary.get('numberOfProcessors', 0)
        max_memory = summary.get('maxMemory', 0)

        # Convert memory from bytes to GB
        max_memory_gb = max_memory / (1024 ** 3) if max_memory > 0 else 0

        markdown += f"### {name}\n\n"
        markdown += "| Metric | Value |\n"
        markdown += "|--------|-------|\n"
        markdown += f"| Duration | {duration} ms |\n"
        markdown += f"| Processors | {processors} |\n"
        markdown += f"| Max Memory | {max_memory_gb:.2f} GB |\n"
        markdown += "\n"

    return markdown

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: performance-to-markdown.py <json_file>")
        sys.exit(1)

    json_file = sys.argv[1]
    markdown = convert_to_markdown(json_file)
    print(markdown)

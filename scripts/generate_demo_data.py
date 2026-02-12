#!/usr/bin/env python3
"""Generate demo Newick trees and metadata CSV files.

Uses only the Python standard library. Outputs a random binary tree and
matching metadata for the sample IDs.
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import random
from dataclasses import dataclass
from typing import List, Optional


@dataclass
class Node:
    name: Optional[str] = None
    length: float = 0.0
    children: Optional[List["Node"]] = None

    def is_leaf(self) -> bool:
        return not self.children


def format_length(value: float) -> str:
    text = f"{value:.6f}".rstrip("0").rstrip(".")
    return text if text else "0"


def to_newick(node: Node) -> str:
    if node.children:
        children_str = ",".join(to_newick(child) for child in node.children)
        name = node.name or ""
        return f"({children_str}){name}:{format_length(node.length)}"
    return f"{node.name}:{format_length(node.length)}"


def build_random_tree(sample_ids: List[str], rng: random.Random) -> Node:
    if len(sample_ids) == 1:
        return Node(name=sample_ids[0], length=rng.uniform(0.02, 0.3), children=None)

    split = rng.randrange(1, len(sample_ids))
    rng.shuffle(sample_ids)
    left = build_random_tree(sample_ids[:split], rng)
    right = build_random_tree(sample_ids[split:], rng)
    return Node(name=None, length=rng.uniform(0.02, 0.2), children=[left, right])


def leaf_distances(node: Node, parent_dist: float = 0.0) -> List[float]:
    current = parent_dist + node.length
    if node.is_leaf():
        return [current]
    distances = []
    for child in node.children or []:
        distances.extend(leaf_distances(child, current))
    return distances


def adjust_to_ultrametric(node: Node, target: float, parent_dist: float = 0.0) -> None:
    current = parent_dist + node.length
    if node.is_leaf():
        node.length += max(0.0, target - current)
        return
    for child in node.children or []:
        adjust_to_ultrametric(child, target, current)



def collect_leaves(node: Node) -> List[str]:
    if node.is_leaf():
        return [node.name] if node.name else []
    leaves: List[str] = []
    for child in node.children or []:
        leaves.extend(collect_leaves(child))
    return leaves


def assign_clades_and_lineages(tree: Node, rng: random.Random) -> dict:
    "Assigns clade and lineage labels based on top-level tree structure."
    clade_labels = ["A", "B", "C", "D"]
    lineage_bases = {"A": "L1", "B": "L2", "C": "L3", "D": "L4"}
    rng.shuffle(clade_labels)

    mapping = {}
    top_children = tree.children or []
    if not top_children:
        return mapping

    for idx, child in enumerate(top_children):
        clade = clade_labels[idx % len(clade_labels)]
        base = lineage_bases.get(clade, "L1")
        subgroups = child.children or [child]
        for sub_idx, subgroup in enumerate(subgroups, start=1):
            lineage = f"{base}.{sub_idx}"
            for leaf in collect_leaves(subgroup):
                mapping[leaf] = {"clade": clade, "lineage": lineage}

    return mapping


def generate_metadata(
    sample_ids: List[str], rng: random.Random, clade_map: dict
) -> List[dict]:
    cities = [
        ("Seattle", "USA"),
        ("Boston", "USA"),
        ("London", "UK"),
        ("Berlin", "Germany"),
        ("Nairobi", "Kenya"),
        ("Sao Paulo", "Brazil"),
        ("Mumbai", "India"),
        ("Sydney", "Australia"),
        ("Tokyo", "Japan"),
        ("Cape Town", "South Africa"),
    ]
    sample_types = ["blood", "feces", "swab", "environmental"]
    resistance = ["none", "low", "moderate", "high"]
    hosts = ["human", "livestock", "wildlife", "environment"]
    fallback_lineages = ["L1", "L2", "L3", "L4"]
    fallback_clades = ["A", "B", "C", "D"]

    start_date = dt.date(2018, 1, 1)
    end_date = dt.date(2024, 12, 31)
    span_days = (end_date - start_date).days

    rows = []
    for sample_id in sample_ids:
        city, country = rng.choice(cities)
        collection_date = start_date + dt.timedelta(days=rng.randint(0, span_days))
        clade_info = clade_map.get(sample_id, {})
        rows.append(
            {
                "sample_id": sample_id,
                "collection_date": collection_date.isoformat(),
                "city": city,
                "country": country,
                "sample_type": rng.choice(sample_types),
                "antimicrobial_resistance": rng.choice(resistance),
                "strain": f"ST{rng.randint(1, 99):02d}",
                "host": rng.choice(hosts),
                "lineage": clade_info.get("lineage", rng.choice(fallback_lineages)),
                "clade": clade_info.get("clade", rng.choice(fallback_clades)),
            }
        )
    return rows


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate demo tree + metadata.")
    parser.add_argument("--samples", type=int, default=24, help="Number of samples (default: 24)")
    parser.add_argument("--ultrameric", action="store_true", help="Force ultrameric tree")
    parser.add_argument("--seed", type=int, default=None, help="Random seed for reproducibility")
    parser.add_argument("--tree-out", default="demo_tree.nwk", help="Output Newick file path")
    parser.add_argument("--metadata-out", default="demo_metadata.csv", help="Output metadata CSV path")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.samples < 2:
        raise SystemExit("--samples must be >= 2")

    rng = random.Random(args.seed)
    sample_ids = [f"S{idx:03d}" for idx in range(1, args.samples + 1)]

    tree = build_random_tree(sample_ids[:], rng)
    if args.ultrameric:
        max_dist = max(leaf_distances(tree))
        adjust_to_ultrametric(tree, max_dist)

    newick = to_newick(tree) + ";\n"
    with open(args.tree_out, "w", encoding="utf-8") as handle:
        handle.write(newick)

    clade_map = assign_clades_and_lineages(tree, rng)
    rows = generate_metadata(sample_ids, rng, clade_map)
    fieldnames = list(rows[0].keys())
    with open(args.metadata_out, "w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    print(f"Wrote {args.tree_out} and {args.metadata_out}")


if __name__ == "__main__":
    main()

# Review 3 Diagram Assets

The SVG files in this directory were rendered from the PlantUML blocks in
[`../diagrams`](../diagrams). SVG is the delivery format for draw.io import and
for report/slide placement because it keeps text and lines sharp when scaled.

## Asset map

| Asset | Source Markdown | Diagram type |
|---|---|---|
| `warehouse-layout-capacity-class.svg` | `warehouse-layout-capacity.md` | Class |
| `warehouse-layout-capacity-sequence.svg` | `warehouse-layout-capacity.md` | Sequence |
| `inventory-receipt-class.svg` | `inventory-receipt.md` | Class |
| `inventory-receipt-sequence.svg` | `inventory-receipt.md` | Sequence |
| `stock-transfer-class.svg` | `stock-transfer.md` | Class |
| `stock-transfer-sequence.svg` | `stock-transfer.md` | Sequence |
| `stock-transfer-state.svg` | `stock-transfer.md` | State |
| `inventory-audit-class.svg` | `inventory-audit.md` | Class |
| `inventory-audit-sequence.svg` | `inventory-audit.md` | Sequence |
| `inventory-audit-state.svg` | `inventory-audit.md` | State |
| `staff-operations-class.svg` | `staff-operations.md` | Class |
| `staff-operations-sequence.svg` | `staff-operations.md` | Sequence |

## Rendering and quality record

- Renderer: PlantUML `1.2026.7`.
- Output format: self-contained SVG with no creator-machine file references.
- Source blocks: 12; rendered files: 12; PlantUML exit status: successful.
- The source uses the Review 3 conventions: monochrome backend diagrams,
  compact class members, action labels on state transitions and no state
  compartments.
- Sequence diagrams contain activation bars on synchronous controller/service
  calls; return messages are kept only where they clarify the main flow.
- The SVG roots have explicit dimensions and view boxes; no clipping warnings
  were reported during rendering.

## Import guidance

1. Import the SVG into draw.io when a vector diagram is needed.
2. Keep the SVG as the review/report asset; do not replace it with a screenshot.
3. If an editable draw.io version is required, import the SVG and use draw.io's
   SVG conversion option where supported. Keep the Markdown PlantUML block as
   the source of truth for future corrections.
4. Before submission, inspect each diagram at 100% zoom and confirm that labels,
   arrowheads, activation bars and state names are readable and not clipped.

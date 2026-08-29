"""Age-adaptation profile resolver — TECHNICAL_MODEL §13.

Resolves ``age_band`` + per-dimension parent overrides into a
``ComplexityProfile`` of **rendering values only**. It carries **no**
``ownership_stage``, stage label, level, or readiness value (INV-8) — that is
structural: there simply is no such field on ``ComplexityProfile``.
"""

from __future__ import annotations

from dataclasses import dataclass, fields

# canonical bands
BAND_3_4 = "3-4"
BAND_5_6 = "5-6"
BAND_7_8 = "7-8"
_BANDS = (BAND_3_4, BAND_5_6, BAND_7_8)


@dataclass(frozen=True)
class ComplexityProfile:
    band: str
    text_style: str            # icon_only | short_label | short_sentence
    audio_narration: str       # always | on_tap
    iconography: str           # large_simple | standard
    quests_shown_at_once: int  # soft layout hint
    interaction: str           # single_tap | tap_drag | tap_drag_order
    task_complexity: str       # single_step | small_multi_step | multi_step_sequence
    reading_requirement: str   # none | minimal | light
    reward_presentation: str   # big_animation | animation_progress | progress_collectibles


_DEFAULTS: dict[str, dict[str, object]] = {
    BAND_3_4: dict(
        text_style="icon_only", audio_narration="always", iconography="large_simple",
        quests_shown_at_once=3, interaction="single_tap", task_complexity="single_step",
        reading_requirement="none", reward_presentation="big_animation",
    ),
    BAND_5_6: dict(
        text_style="short_label", audio_narration="on_tap", iconography="standard",
        quests_shown_at_once=5, interaction="tap_drag", task_complexity="small_multi_step",
        reading_requirement="minimal", reward_presentation="animation_progress",
    ),
    BAND_7_8: dict(
        text_style="short_sentence", audio_narration="on_tap", iconography="standard",
        quests_shown_at_once=7, interaction="tap_drag_order", task_complexity="multi_step_sequence",
        reading_requirement="light", reward_presentation="progress_collectibles",
    ),
}

_DIMENSIONS = tuple(f.name for f in fields(ComplexityProfile) if f.name != "band")


def normalize_band(age_band: str) -> str:
    """Map a free-form band string to a canonical one; unknown → 5-6 (middle)."""
    b = (age_band or "").strip().replace("–", "-").replace(" ", "")
    if b in _BANDS:
        return b
    if b in {"3", "4", "34"}:
        return BAND_3_4
    if b in {"7", "8", "78"}:
        return BAND_7_8
    return BAND_5_6


def resolve_complexity_profile(age_band: str, overrides: dict[str, str] | None = None) -> ComplexityProfile:
    """TECHNICAL_MODEL §13: ``profile[dim] = override[dim] if present else band_default(band)[dim]``.
    Every field always has a resolved value. Overrides are per-dimension.
    """
    band = normalize_band(age_band)
    base = dict(_DEFAULTS[band])
    ov = overrides or {}
    for dim in _DIMENSIONS:
        if dim in ov and ov[dim] not in (None, ""):
            val = ov[dim]
            base[dim] = int(val) if dim == "quests_shown_at_once" else val
    return ComplexityProfile(band=band, **base)  # type: ignore[arg-type]

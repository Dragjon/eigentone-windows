"""Rewords the Android app's strings for Windows: a PC, a mouse and Windows settings rather than a phone.

Run from the repository root after copying strings_ui.xml from Android. It is safe to run again.
Whole texts that describe a phone's way of working are rewritten in REWRITE; the rest get the small,
mechanical swaps in SWAPS, such as "tap" to "click".
"""
import pathlib
import re

PATH = pathlib.Path("app/src/main/composeResources/values/strings_ui.xml")

REWRITE = {
    "help_graphic_body": "Fixed faders from deep bass to high treble. They show the whole sound at each band, the preset's own curve plus your change, so +3 dB means 3 dB louder than no EQ at all. Picking a preset moves them to its curve, and Reset puts them back there. Where a preset cuts further than a fader reaches, the fader rests at the bottom and the readout gives the real figure. Your changes are saved with the preset.\\n\\nPrecision (crosshair) only moves a fader when you grab its cap, then moves it from where it is. Double-click a fader to return it to the preset's saved setting. Smooth (wave) lets you drag across the faders to draw a curve.",
    "help_fader_controls_body": "Precision only moves a fader when you grab its cap, then moves it from where it is, so a click on the rail changes nothing. Smooth lets you drag across the faders to draw a whole curve in one go. In either mode, double-click a fader to return it to the preset's saved setting.",
    "help_fader_touch_body": "Precision keeps hold of the fader you grabbed, even if the pointer drifts sideways, and moves it up or down from where it is, so a click only selects it and never makes it jump. Smooth lets you drag across the faders to draw a whole curve in one go, and shows them as a continuous line.",
    "help_analyser_body": "Shows the level of each frequency behind the curve, from what Eigentone's plug-in plays, only while this screen is open. The lowest bands look coarse.\\n\\nThe slim bar beside the graph shows the output level. Click it to read about it.",
    "help_meter_body": "The slim bar shows how loud everything playing is, read by Eigentone's plug-in. The fill is the average level and the line is the peak, held for a moment so short peaks can be read. The top is full scale.\\n\\nIt turns red when peaks reach full scale, where sound can distort. If it does, turn the preamp down, or turn on automatic headroom or the limiter in Settings.\\n\\nThe level is read before the Windows volume, so treat it as a guide to headroom rather than to how loud it is in your ears.",
    "help_surround_body": "Makes the sound feel wider than your head, as if from speakers in front of you, using a model of how sound reaches each ear. It doesn't track head movement or create real surround channels. Higher values can make vocals feel less centred. This is saved with the preset.",
    "help_console_body": "Shows what Eigentone is doing as it happens: what it sent to the audio engine, what the engine accepted, and any errors. Copy or share it with a bug report.",
    "help_theme_body": "Windows follows your Windows light or dark setting. Light suits bright places, and Dark suits low light. The sound isn't affected.",
    "help_theme_colour_body": "The colour of whatever is active: the curve, the selection and what's on. Windows accent uses the accent colour set in Windows. Otherwise pick a swatch, or the rainbow to choose any hue with the slider. The sound isn't affected.",
    "help_text_size_body": "Makes the app's text larger, by about a seventh or a third. Windows' own text size setting still applies on top.",
    "welcome_hello_body": "An equaliser for everything your PC plays, that keeps the sound right as you switch between headphones and speakers.",
    "welcome_hands_where": "You can change this any time in Settings, under Appearance, Advanced controls.",
    "ui_measure_intro": "Eigentone plays a set of tones through the speaker for about 15 seconds while the microphone listens, three times, from three spots a little apart around where you sit. Then it works out a gentle correction.\\n\\nSet the volume to how loud you usually listen, keep the room quiet, and put the microphone where your head would be, facing the speaker. The EQ steps aside while the tones play. Turn off any microphone effects, such as noise suppression or Voice Clarity, in Windows' sound settings first.",
    "ui_measure_phone_mic": "This uses the microphone set as the default in Windows, such as your laptop's or webcam's, which hears some frequencies more than others, mostly the deepest bass and the highest treble. The correction leaves those ends alone and keeps boosts small, so it stays safe. A USB measurement microphone gives a more exact result.",
    "ui_measure_move": "Spot %1$d of %2$d done. Move the microphone about 30 cm, still around where you sit, then measure the next one.",
    "ui_measure_failed": "Windows wouldn't play and record at the same time. Close any app using the microphone and try again.",
    "help_measure_body": "For speakers, where the room changes the sound as much as the speakers do. Eigentone plays steady tones from deep bass to high treble and the microphone reads how loud each one arrives where you sit. Three spots are averaged, because one exact spot has dips a step away doesn't.\\n\\nThe correction cuts peaks, which rooms are full of, and lifts dips only a little, since filling a dip mostly costs headroom without being heard. A laptop's or webcam's microphone isn't even across the range, so treat the result as a good start rather than a lab measurement. A USB measurement microphone gives a more exact one.\\n\\nNothing is recorded or kept. Only each tone's level is read.",
    "help_diagnostics_body": "Copies technical details about your PC and the audio engine, for reporting a problem. Nothing is sent anywhere unless you paste it somewhere.",
    "help_crash_report_body": "A technical record of the last crash, kept only on this PC. You choose whether to share it, and deleting it removes it.",
    "help_start_on_boot_body": "Turns the EQ back on when you sign in to Windows, if it was on before.",
    "help_status_repairs_body": "How many times Eigentone has rebuilt the effect after losing it since it last started, such as when the Windows audio service restarts. Occasional repairs are fine, but a rising number suggests a problem.",
    "help_loudness_body": "Ears lose bass, and some treble, faster than midrange as music gets quieter. This keeps the balance you hear at your usual volume as you turn it down, using the ISO 226 equal-loudness contours. It follows the Windows volume, so it's an estimate of the level at your ears, and each output keeps its own usual volume.",
    "help_loudness_strength_body": "How much of the correction to use. 100% is the full amount from the contours. Lower it if quiet listening sounds too boomy. Each output keeps its own, so your speakers can use less than your headphones.",
    "ui_usual_volume_for": "For %1$s. Windows is %2$s dB below full now.",
    "help_usual_volume_body": "The volume you normally listen at, where the music sounds balanced. Nothing is added there or louder, and more is added the further below it you go. Set your usual volume, play music, and click Use the current volume. Each output keeps its own.",
    "help_sweep_level_body": "How loud the tone is, before the Windows volume. It can't go above a set limit, so it stays gentle at a normal volume.",
    "help_compressor_threshold_body": "Anything louder than this gets turned down. Lower values compress more of the music. The Windows volume doesn't change this, because it happens before the volume control.",
    "help_output_preset_body": "Choose the preset that loads for the output playing now, such as your headphones or your PC's speakers.",
    "ui_device_scope_phone": "The PC's own speakers. Kept for them, and used again whenever they play.",
    "ui_tab_phone": "This PC",
    "ui_tamers_unavailable": "These need extra bands the audio engine isn't giving, so they have no effect right now.",
    "help_bass_character_body": "How the added bass behaves over time. Natural is a plain lift. Punch lets each kick or bass hit through, then eases its tail, so hits stand out. Sustain catches each hit and brings its tail up, so bass sounds fuller and lasts longer. Punch and Sustain work on the deep bass below about 110 Hz.",
    "ui_this_phone_wouldn_t_play_the_tone": "Windows wouldn't play the tone.",
    "ui_this_phone_wouldn_t_play_the": "Windows wouldn't play the tone. Try Play again.",
    "ui_this_phone_refused_the_analyser": "The analyser isn't available right now.",
    "ui_this_phone_only_allowed_bands_so": "The engine only allowed %1$s bands, so narrow filters will be less accurate.",
    "tour_presets_body": "Click a preset to switch to it. The one in use, your favourites and your own come first.",
    "tour_tabs_body": "Tune shapes the curve and the effects. Presets holds your sounds. Devices fits your sound to what's playing, such as headphones or speakers.",
    "ui_eq_in_a_headphone_app_such": "EQ in other audio software, such as Realtek Audio Console, Dolby Access, Nahimic or your headphones' own app, adds to this one, so changes can apply twice. For the clearest results, set it to flat or default, and turn off any bass boost.",
    "ui_eq_in_a_headphone_app_such_2": "EQ in other audio software, such as Realtek Audio Console or your headphones' own app, adds to this one. Set it to flat or default, or changes can apply twice.",
    "ui_measure_usb_mic": "A USB microphone is connected. Eigentone records from the microphone set as the default in Windows' sound settings, so make sure it's that one.",
    "help_status_bands_body": "How finely your curve is reproduced. Eigentone's plug-in builds one smooth filter for each ear from 256 bands. None means the plug-in isn't running on the output playing now.",
    "help_status_readback_body": "Whether the plug-in has taken your latest settings, as 256/256 once it has. It takes them as sound plays, so before anything has played it says Not checked yet.",
    "help_status_control_body": "Whether Eigentone's plug-in is in the playing output's effects. A sound driver update can replace them, and then Eigentone's settings stop applying until it's added back.",
    "help_status_mode_body": "How Eigentone reaches the sound. On Windows its plug-in runs inside Windows' audio engine, for every app playing to the output.",
    "help_status_running_body": "Whether Eigentone's background service is running, which passes your settings to the plug-in.",
}

# Small swaps, in order, applied to every string not rewritten above.
SWAPS = [
    (r"\bDouble[- ]tap\b", "Double-click"),
    (r"\bdouble[- ]tap\b", "double-click"),
    (r"\bTapping\b", "Clicking"),
    (r"\btapping\b", "clicking"),
    (r"\bTap\b", "Click"),
    (r"\btap\b", "click"),
    (r"\bTaps\b", "Clicks"),
    (r"\btaps\b", "clicks"),
    (r"\bthe phone speaker\b", "the PC's speakers"),
    (r"\bphone speaker\b", "PC speakers"),
    (r"\bThis phone\b", "This PC"),
    (r"\bthis phone\b", "this PC"),
    (r"\byour phone's\b", "your PC's"),
    (r"\bthe phone's\b", "the PC's"),
    (r"\bYour phone's\b", "Your PC's"),
    (r"\byour phone\b", "your PC"),
    (r"\bthe phone\b", "the PC"),
    (r"\bon a phone\b", "on a PC"),
    (r"\bPhone volume\b", "The Windows volume"),
]

ENTRY = re.compile(r'(<string name="([a-z0-9_]+)"[^>]*>)(.*?)(</string>)', re.S)


def reword(match: re.Match) -> str:
    start, name, text, end = match.groups()
    if name in REWRITE:
        return start + REWRITE[name] + end
    for pattern, replacement in SWAPS:
        text = re.sub(pattern, replacement, text)
    return start + text + end


def main() -> None:
    source = PATH.read_text(encoding="utf-8")
    missing = [name for name in REWRITE if f'name="{name}"' not in source]
    if missing:
        print("Not in strings_ui.xml:", ", ".join(missing))
    result = ENTRY.sub(reword, source)
    PATH.write_text(result, encoding="utf-8", newline="\n")
    print("Reworded", sum(1 for _ in ENTRY.finditer(source)), "strings checked")


if __name__ == "__main__":
    main()

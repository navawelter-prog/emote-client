#!/usr/bin/env python3
"""
Wandelt ein Tanzvideo in eine .emote-Datei fuer den Emote Client um.

Benoetigt: ffmpeg im PATH, pip install mediapipe numpy scipy opencv-python
und das Modell pose_landmarker_full.task (Link siehe --model).
Benutzung:
    python video_to_emote.py tanz.mp4 dance.emote --model pose_landmarker_full.task [--fps 15] [--no-loop]

Die Person im Video sollte ganz zu sehen sein und moeglichst zur Kamera schauen.
Pro Frame werden 18 Zahlen gespeichert: Drehwinkel (x y z, Radiant) fuer
Kopf, Koerper, rechten Arm, linken Arm, rechtes Bein, linkes Bein.

Koordinaten: MediaPipe-Weltkoordinaten (x = Bild rechts, y = unten, z = von der Kamera weg)
entsprechen direkt den Modellachsen von Minecraft (x = links vom Spieler, y = unten, z = nach hinten).
"""
import argparse
import math
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
from scipy.signal import savgol_filter

PARTS = ["head", "body", "right_arm", "left_arm", "right_leg", "left_leg"]

# MediaPipe-Pose-Indizes
NOSE, L_EAR, R_EAR = 0, 7, 8
L_SH, R_SH, L_EL, R_EL, L_WR, R_WR = 11, 12, 13, 14, 15, 16
L_HIP, R_HIP, L_KNEE, R_KNEE, L_ANK, R_ANK = 23, 24, 25, 26, 27, 28
L_HEEL, R_HEEL, L_TOE, R_TOE = 29, 30, 31, 32


def unit(v):
    n = np.linalg.norm(v)
    return v / n if n > 1e-6 else np.array([0.0, 1.0, 0.0])


def limb_angles(direction):
    """Richtung eines nach unten haengenden Teils -> (xRot, zRot). yRot (Verdrehung) bleibt 0."""
    v = unit(direction)
    x_rot = math.asin(max(-1.0, min(1.0, v[2])))
    z_rot = math.atan2(-v[0], v[1])
    return x_rot, z_rot


def extract_frames(video, out_dir, fps):
    cmd = ["ffmpeg", "-v", "error", "-i", str(video), "-vf", f"fps={fps},scale=-2:720",
           str(Path(out_dir) / "f_%04d.jpg")]
    subprocess.run(cmd, check=True)
    return sorted(Path(out_dir).glob("f_*.jpg"))


def detect(frames, model_path, work_fps):
    """Nutzt die aktuelle MediaPipe-Tasks-Schnittstelle (ungetestet, ohne das Modell nicht ausfuehrbar gewesen)."""
    import cv2
    import mediapipe as mp
    from mediapipe.tasks import python as mp_python
    from mediapipe.tasks.python import vision

    options = vision.PoseLandmarkerOptions(
        base_options=mp_python.BaseOptions(model_asset_path=model_path),
        running_mode=vision.RunningMode.VIDEO,
        num_poses=1)
    result, last = [], None
    with vision.PoseLandmarker.create_from_options(options) as landmarker:
        for n, f in enumerate(frames):
            img = cv2.cvtColor(cv2.imread(str(f)), cv2.COLOR_BGR2RGB)
            image = mp.Image(image_format=mp.ImageFormat.SRGB, data=img)
            res = landmarker.detect_for_video(image, int(n * 1000 / work_fps))
            if res.pose_world_landmarks:
                last = np.array([[p.x, p.y, p.z] for p in res.pose_world_landmarks[0]])
            result.append(last)  # bei Aussetzern letzte gueltige Haltung behalten
    first = next((r for r in result if r is not None), None)
    if first is None:
        sys.exit("Keine Person erkannt. Ist die Person im Bild?")
    return np.array([r if r is not None else first for r in result])


def depth_axis_is_reversed(lm):
    """
    Die Person schaut zur Kamera, die Zehen zeigen also im Schnitt zur Kamera hin.
    Modellachse z soll 'von der Kamera weg' sein, also muessen die Zehen kleineres z als die Fersen haben.
    """
    toe_minus_heel = (lm[:, [L_TOE, R_TOE], 2] - lm[:, [L_HEEL, R_HEEL], 2]).mean()
    return toe_minus_heel > 0


def compute(lm):
    n = len(lm)
    out = np.zeros((n, 6, 3))
    for i in range(n):
        p = lm[i]
        # Kopf
        fwd = p[NOSE] - (p[L_EAR] + p[R_EAR]) / 2
        yaw = math.atan2(-fwd[0], -fwd[2])
        pitch = math.asin(max(-1.0, min(1.0, unit(fwd)[1])))
        ear = p[L_EAR] - p[R_EAR]
        roll = math.atan2(ear[1], ear[0])
        out[i, 0] = [pitch * 0.8, yaw * 0.8, roll * 0.8]
        # Koerper
        mid_sh, mid_hip = (p[L_SH] + p[R_SH]) / 2, (p[L_HIP] + p[R_HIP]) / 2
        bx, bz = limb_angles(mid_hip - mid_sh)
        sh = p[L_SH] - p[R_SH]
        twist = math.atan2(-sh[2], sh[0])
        out[i, 1] = [bx, twist, bz]
        # Arme: Mischung aus Oberarm und Gesamtarm, weil das Minecraft-Modell keinen Ellbogen hat
        for idx, (s, e, w) in ((2, (R_SH, R_EL, R_WR)), (3, (L_SH, L_EL, L_WR))):
            d = 0.65 * unit(p[e] - p[s]) + 0.35 * unit(p[w] - p[s])
            ax, az = limb_angles(d)
            out[i, idx] = [ax, 0.0, az]
        # Beine
        for idx, (h, k, a) in ((4, (R_HIP, R_KNEE, R_ANK)), (5, (L_HIP, L_KNEE, L_ANK))):
            d = 0.4 * unit(p[k] - p[h]) + 0.6 * unit(p[a] - p[h])
            lx, lz = limb_angles(d)
            out[i, idx] = [lx, 0.0, lz]
    return out


def unwrap_all(angles):
    for part in range(6):
        for axis in range(3):
            angles[:, part, axis] = np.unwrap(angles[:, part, axis])
    return angles


def make_loop(angles, fps, blend_seconds=0.4):
    """Haengt Uebergangsframes vom Ende zum Anfang an, damit die Schleife nicht springt."""
    k = max(2, int(round(blend_seconds * fps)))
    first, last = angles[0], angles[-1]
    # Winkel auf kuerzesten Weg zum Startwert bringen (verhindert Drehung um 2*pi)
    first = last + (((first - last) + math.pi) % (2 * math.pi) - math.pi)
    extra = [last + (first - last) * (j / (k + 1)) for j in range(1, k + 1)]
    return np.concatenate([angles, np.array(extra)])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("video")
    ap.add_argument("output")
    ap.add_argument("--model", required=True,
                    help="Pfad zu pose_landmarker_full.task (Download: "
                         "https://storage.googleapis.com/mediapipe-models/pose_landmarker/"
                         "pose_landmarker_full/float16/latest/pose_landmarker_full.task)")
    ap.add_argument("--fps", type=int, default=15)
    ap.add_argument("--no-loop", action="store_true")
    args = ap.parse_args()

    work_fps = 30
    with tempfile.TemporaryDirectory() as tmp:
        frames = extract_frames(args.video, tmp, work_fps)
        print(f"{len(frames)} Frames gelesen, erkenne Haltung ...")
        lm = detect(frames, args.model, work_fps)

    reversed_z = depth_axis_is_reversed(lm)
    if reversed_z:
        lm[:, :, 2] *= -1.0
    print(f"Tiefen-Achse: {'umgedreht' if reversed_z else 'unveraendert'}")

    # Rauschen aus den Rohpunkten filtern, dann Winkel berechnen
    win = 9 if len(lm) >= 9 else (len(lm) // 2) * 2 - 1
    lm = savgol_filter(lm, window_length=win, polyorder=2, axis=0)
    angles = unwrap_all(compute(lm))
    angles = savgol_filter(angles, window_length=win, polyorder=2, axis=0)

    step = work_fps // args.fps
    angles = angles[::step]
    loop = not args.no_loop
    if loop:
        angles = make_loop(angles, args.fps)

    with open(args.output, "w", encoding="utf-8") as f:
        f.write("# emote v1: head body right_arm left_arm right_leg left_leg (je x y z, Radiant)\n")
        f.write(f"fps {args.fps}\nloop {1 if loop else 0}\n")
        for frame in angles:
            f.write(" ".join(f"{v:.3f}" for v in frame.reshape(-1)) + "\n")
    print(f"{len(angles)} Keyframes -> {args.output} ({len(angles) / args.fps:.1f} s)")


if __name__ == "__main__":
    main()

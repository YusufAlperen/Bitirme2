import numpy as np
import os
import mediapipe as mp
from mediapipe.tasks.python import vision
import cv2

anaGirdi = "D:/Bitirme2/10_kelime_ile_test/TestVeriSeti(10Kelime)"
POSE_MODEL = "D:/Bitirme2/Landmarks/pose_landmarker.task"
HAND_MODEL = "D:/Bitirme2/Landmarks/hand_landmarker.task"

BaseOptions = mp.tasks.BaseOptions
VisionRunningMode = mp.tasks.vision.RunningMode

def kareleri_ornekle(frames):
    toplamKare = len(frames)
    if toplamKare == 0:
        return [np.zeros(225)] * 30
    if toplamKare == 30:
        return frames
    secilenSayilar = np.linspace(0, toplamKare - 1, 30).astype(int)
    return [frames[i] for i in secilenSayilar]

siniflar = [d for d in os.listdir(anaGirdi) if os.path.isdir(os.path.join(anaGirdi, d))]
labelMap = {label: num for num, label in enumerate(siniflar)}

with open('labels.txt', 'w') as f:
    for sinif in siniflar:
        f.write(f"{sinif}\n")

X_verileri = []
y_etiketleri = []

pose_options = vision.PoseLandmarkerOptions(
    base_options=BaseOptions(model_asset_path=POSE_MODEL),
    running_mode=VisionRunningMode.VIDEO)

hand_options = vision.HandLandmarkerOptions(
    base_options=BaseOptions(model_asset_path=HAND_MODEL),
    running_mode=VisionRunningMode.VIDEO, 
    num_hands=2)

with vision.PoseLandmarker.create_from_options(pose_options) as pose_det, \
     vision.HandLandmarker.create_from_options(hand_options) as hand_det:

    for sinif in siniflar:
        girYol = os.path.join(anaGirdi, sinif)
        gecerliVideolar = [d for d in os.listdir(girYol) if d.lower().endswith('mp4') and "depth" not in d.lower()]
        
        print(f"İşleniyor: {sinif} ({len(gecerliVideolar)} video)")

        for dosya in gecerliVideolar:
            videoYolu = os.path.join(girYol, dosya)
            videoKareleri = []
            cap = cv2.VideoCapture(videoYolu)
            sonZaman = -1

            while cap.isOpened():
                ret, frame = cap.read()
                if not ret:
                    break
                
                frame = cv2.resize(frame, (640, 480))
                zaman = int(cap.get(cv2.CAP_PROP_POS_MSEC))
                if zaman <= sonZaman:
                    zaman = sonZaman + 1
                sonZaman = zaman

                mp_img = mp.Image(image_format=mp.ImageFormat.SRGB, data=cv2.cvtColor(frame, cv2.COLOR_BGR2RGB))

                pose_data = np.zeros(99)
                res_pose = pose_det.detect_for_video(mp_img, zaman) 
                if res_pose.pose_landmarks: 
                    pose_data = np.array([[lm.x, lm.y, lm.z] for lm in res_pose.pose_landmarks[0]]).flatten()

                lh_data = np.zeros(63)
                rh_data = np.zeros(63)
                res_hand = hand_det.detect_for_video(mp_img, zaman)
                if res_hand.hand_landmarks:
                    for lms, info in zip(res_hand.hand_landmarks, res_hand.handedness):
                        kategori = info[0].category_name 
                        koordinatlar = np.array([[lm.x, lm.y, lm.z] for lm in lms]).flatten()
                        if kategori == 'Left': lh_data = koordinatlar
                        elif kategori == 'Right': rh_data = koordinatlar

                videoKareleri.append(np.concatenate([pose_data, lh_data, rh_data]))
            
            cap.release()
            X_verileri.append(kareleri_ornekle(videoKareleri))
            y_etiketleri.append(labelMap[sinif])

X = np.array(X_verileri)
y = np.array(y_etiketleri)

np.save('X_data.npy', X)
np.save('y_data.npy', y)

print(f"İşlem Tamamlandı. Veri Şekli: {X.shape}")
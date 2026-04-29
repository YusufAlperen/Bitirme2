import os
import pandas as pd
import numpy as np
import cv2
from tqdm import tqdm

# --- 1. Adım: Sabitlerin Tanımlanması ---

HAND_CONNECTIONS = [
    (0, 1), (1, 2), (2, 3), (3, 4),     # Başparmak
    (0, 5), (5, 6), (6, 7), (7, 8),     # İşaret parmağı
    (5, 9), (9, 10), (10, 11), (11, 12), # Orta parmak
    (9, 13), (13, 14), (14, 15), (15, 16), # Yüzük parmağı
    (13, 17), (0, 17), (17, 18), (18, 19), (19, 20) # Serçe parmak ve avuç içi
]
IMAGE_SIZE = 128

# GÜNCELLENDİ: OpenCV BGR formatında renkler
COLOR_LEFT_HAND = (0, 0, 255)  # Kırmızı (Sol El için)
COLOR_RIGHT_HAND = (0, 255, 0) # Yeşil (Sağ El için)

# --- 2. Adım: İskelet Çizim Fonksiyonu (GÜNCELLENDİ) ---

def draw_hand_skeleton(row_data, img_size=IMAGE_SIZE):
    """
    126 özellikli satırı alır ve 64x64x3 (Renkli) bir el iskeleti görüntüsü döndürür.
    Sol El = Kırmızı, Sağ El = Yeşil
    """
    # GÜNCELLENDİ: 3 kanallı (RGB) boş görüntü oluştur
    image = np.zeros((img_size, img_size, 3), dtype=np.uint8)
    
    try:
        landmarks = np.array(row_data).reshape(42, 3)
    except ValueError:
        return None

    landmarks_xy = landmarks[:, :2] 
    
    non_zero_coords = landmarks_xy[np.any(landmarks_xy != 0, axis=1)]
    if non_zero_coords.shape[0] == 0:
        return image 

    min_vals = np.min(non_zero_coords, axis=0)
    max_vals = np.max(non_zero_coords, axis=0)
    
    scale = max_vals - min_vals
    scale[scale == 0] = 1 
    
    scaled_coords = (landmarks_xy - min_vals) / scale
    scaled_coords[np.all(landmarks_xy == 0, axis=1)] = -1 

    padding = img_size * 0.05
    img_scale = img_size - (2 * padding)
    
    final_coords = (scaled_coords * img_scale + padding).astype(int)

    # 7. İki eli de FARKLI RENKLERDE çiz
    
    # Sol El (indeks 0-20) KIRMIZI renkle çiz
    for connection in HAND_CONNECTIONS:
        idx1 = connection[0]
        idx2 = connection[1]
        
        p1 = final_coords[idx1]
        p2 = final_coords[idx2]
        
        if (p1[0] > 0 and p1[1] > 0 and p2[0] > 0 and p2[1] > 0):
            cv2.line(image, (p1[0], p1[1]), (p2[0], p2[1]), COLOR_LEFT_HAND, 2)

    # Sağ El (indeks 21-41) YEŞİL renkle çiz
    for connection in HAND_CONNECTIONS:
        idx1 = connection[0] + 21
        idx2 = connection[1] + 21
        
        p1 = final_coords[idx1]
        p2 = final_coords[idx2]
        
        if (p1[0] > 0 and p1[1] > 0 and p2[0] > 0 and p2[1] > 0):
            cv2.line(image, (p1[0], p1[1]), (p2[0], p2[1]), COLOR_RIGHT_HAND, 2)
            
    return image

# --- 3. Adım: Ana İşlem (Başlıklı CSV için) ---

def process_csv(csv_path, label_column, output_dir):
    try:
        data = pd.read_csv(csv_path)
    except FileNotFoundError:
        print(f"Hata: {csv_path} dosyası bulunamadı.")
        return
        
    print(f"Toplam {len(data)} satır veri bulundu. İşlem başlıyor...")

    try:
        feature_columns = data.columns.drop(label_column)
    except KeyError:
        print(f"Hata: '{label_column}' adında bir sütun CSV dosyasında bulunamadı.")
        return

    if len(feature_columns) != 126:
        print(f"Uyarı: 126 özellik sütunu bekleniyordu, {len(feature_columns)} adet bulundu.")

    os.makedirs(output_dir, exist_ok=True)

    for index, row in tqdm(data.iterrows(), total=data.shape[0]):
        label = row[label_column]
        landmark_data = row[feature_columns].values
        
        label_dir = os.path.join(output_dir, str(label))
        os.makedirs(label_dir, exist_ok=True)
        
        skeleton_image = draw_hand_skeleton(landmark_data, IMAGE_SIZE)
        
        if skeleton_image is None:
            continue
            
        image_name = f"{label}_{index}.png"
        image_path = os.path.join(label_dir, image_name)
        cv2.imwrite(image_path, skeleton_image)

    print("İşlem tamamlandı.")
    print(f"Renkli görüntüler '{output_dir}' klasörüne kaydedildi.")

# --- 4. Adım: Kodu Çalıştırma ---
if __name__ == "__main__":
    
    # Kendi CSV dosyanın adını yaz
    CSV_FILE_PATH = "landmark_verilerim_filtrelenmis.csv" 
    
    # CSV'deki etiket sütununun adı
    LABEL_COLUMN_NAME = "label" 
    
    # Oluşturulacak görüntülerin klasörü
    OUTPUT_IMAGE_DIR = "iskelet_goruntuleri_filtreli_128"

    process_csv(CSV_FILE_PATH, LABEL_COLUMN_NAME, OUTPUT_IMAGE_DIR)
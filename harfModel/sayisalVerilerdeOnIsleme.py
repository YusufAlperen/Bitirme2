import pandas as pd

CSV_FILE_PATH = "hand_landmarks_dataset_double.csv" 


OUTPUT_CSV_PATH = "landmark_verilerim_filtrelenmis.csv"

IKI_EL_GEREKEN_HARFLER = [
    "A","B","D","E","F","G","H","J","K","M","N","R","S","T","Y","Z" 
]


def filter_dataset(csv_path, output_path, required_labels):
    print(f"'{csv_path}' dosyası yükleniyor...")
    try:

        df = pd.read_csv(csv_path)
    except FileNotFoundError:
        print(f"HATA: {csv_path} dosyası bulunamadı.")
        return
        
    initial_rows = len(df)
    print(f"Dosya yüklendi. Başlangıçtaki satır sayısı: {initial_rows}")
    

    left_hand_present = df.iloc[:, 0:63].abs().sum(axis=1) > 0
    

    right_hand_present = df.iloc[:, 63:126].abs().sum(axis=1) > 0
    

    both_hands_present = left_hand_present & right_hand_present
    
    is_two_hand_required = df['label'].isin(required_labels)
    
    
    condition_to_keep = ~is_two_hand_required | both_hands_present
    
    filtered_df = df[condition_to_keep].copy()
    
    final_rows = len(filtered_df)
    dropped_rows = initial_rows - final_rows
    
    print("Filtreleme tamamlandı.")
    print(f"Kalan satır sayısı: {final_rows}")
    print(f"Ayıklanan (silinen) satır sayısı: {dropped_rows}")
    

    filtered_df.to_csv(output_path, index=False)
    print(f"Filtrelenmiş veri '{output_path}' dosyasına kaydedildi.")

if __name__ == "__main__":
    filter_dataset(CSV_FILE_PATH, OUTPUT_CSV_PATH, IKI_EL_GEREKEN_HARFLER)
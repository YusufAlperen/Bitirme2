import numpy as np

# Veri yükle
X = np.load('D:/Bitirme 2 git/kelimeModel/X_data_mesafe_norm.npy')
y = np.load('D:/Bitirme 2 git/kelimeModel/y_data.npy')

def add_noise(seq, sigma=0.005):
    return seq + np.random.normal(0, sigma, seq.shape).astype(np.float32)

# Gürültülü kopya oluştur
X_gurultulu = np.array([add_noise(seq) for seq in X], dtype=np.float32)

# Orijinal + gürültülü birleştir
X_birlesik = np.concatenate([X, X_gurultulu], axis=0)
y_birlesik = np.concatenate([y, y], axis=0)

# Shuffle
idx = np.random.permutation(len(X_birlesik))
X_birlesik = X_birlesik[idx]
y_birlesik = y_birlesik[idx]

# Kaydet
np.save('D:/Bitirme 2 git/kelimeModel/X_data_augmented.npy', X_birlesik)
np.save('D:/Bitirme 2 git/kelimeModel/y_data_augmented.npy', y_birlesik)

print(f"Orijinal: {X.shape}")
print(f"Augmented: {X_birlesik.shape}")
print(f"y Augmented: {y_birlesik.shape}")
print("Kaydedildi!")
import numpy as np


X_ham = np.load("D:/Bitirme2/tum_kelimeler_2/X_data.npy") 


X_yeni = np.zeros((X_ham.shape[0], X_ham.shape[1], 201), dtype=np.float32)

for i in range(X_ham.shape[0]):
    for t in range(X_ham.shape[1]):
        kare = X_ham[i, t]
        
        if np.sum(kare) == 0: 
            continue
            
  
        ust_govde = kare[0:75] 
        sol_el = kare[99:162]
        sag_el = kare[162:225]
        

        ham_201 = np.concatenate([ust_govde, sol_el, sag_el])
        
  
        burun_x, burun_y, burun_z = ham_201[0], ham_201[1], ham_201[2]
        

        ls_x, ls_y = ham_201[33], ham_201[34] 
        rs_x, rs_y = ham_201[36], ham_201[37] 
        
  
        omuz_genisligi = np.sqrt((ls_x - rs_x)**2 + (ls_y - rs_y)**2)
        if omuz_genisligi == 0: omuz_genisligi = 1.0 
        
        norm_kare = np.zeros(201)
        for j in range(67): 
            norm_kare[j*3]     = (ham_201[j*3] - burun_x) / omuz_genisligi
            norm_kare[j*3 + 1] = (ham_201[j*3 + 1] - burun_y) / omuz_genisligi
            norm_kare[j*3 + 2] = (ham_201[j*3 + 2] - burun_z) / omuz_genisligi
            
        X_yeni[i, t] = norm_kare

# Kaydet
np.save('D:/Bitirme2/tum_kelimeler_2/X_data_mesafe_norm.npy', X_yeni)
print("bitti")
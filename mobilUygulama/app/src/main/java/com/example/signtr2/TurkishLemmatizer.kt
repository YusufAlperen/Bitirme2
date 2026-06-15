package com.example.signtr2

class TurkishLemmatizer(labels: List<String>) {

    private val asciiToOriginal = labels.associateBy({ asciify(it) }, { it })
    private val sortedLabels = asciiToOriginal.keys.sortedByDescending { it.length }

    fun cumleyiCoz(cumle: String): List<String> {
        val kelimeler = cumle.lowercase()
            .replace(Regex("[^a-zçğıöşü ]"), " ")
            .split(" ")
            .filter { it.isNotBlank() }

        val sonuc = mutableListOf<String>()

        for (kelime in kelimeler) {
            val asciiKelime = asciify(kelime)

            if (asciiToOriginal.containsKey(asciiKelime)) {
                sonuc.add(asciiToOriginal[asciiKelime]!!)
                continue
            }

            val baslayan = sortedLabels.firstOrNull { asciiKelime.startsWith(it) }
            if (baslayan != null) {
                sonuc.add(asciiToOriginal[baslayan]!!)
                continue
            }

            val icindeGecen = sortedLabels.firstOrNull { asciiKelime.contains(it) }
            if (icindeGecen != null) {
                sonuc.add(asciiToOriginal[icindeGecen]!!)
            }
        }

        return sonuc
    }

    companion object {
        fun asciify(s: String): String {
            return s.lowercase()
                .replace("ç", "c")
                .replace("ğ", "g")
                .replace("ı", "i")
                .replace("ö", "o")
                .replace("ş", "s")
                .replace("ü", "u")
                .replace("â", "a")
                .replace("î", "i")
                .replace("û", "u")
        }
    }
}
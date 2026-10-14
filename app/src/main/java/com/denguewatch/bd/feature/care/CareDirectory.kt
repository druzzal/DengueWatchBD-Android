package com.denguewatch.bd.feature.care

import com.denguewatch.bd.i18n.AppLanguage
import com.denguewatch.bd.model.Division

/**
 * National emergency lines. Only numbers that are stable, nationally published
 * and free to dial are listed — an emergency screen is the worst possible place
 * for a number that might be wrong.
 */
data class EmergencyNumber(val id: String, val dial: String) {
    /**
     * 999 is a true emergency line; the other two are advice and information
     * services. Painting all three as the same urgent red button told the
     * reader nothing about which one to press when it matters.
     */
    val isEmergency: Boolean get() = id == "999"
    val nameKey: String get() = "care.sos.$id.name"
    val detailKey: String get() = "care.sos.$id.detail"

    companion object {
        val all = listOf(
            EmergencyNumber("999", "999"),
            EmergencyNumber("16263", "16263"),
            EmergencyNumber("333", "333"),
        )
    }
}

/**
 * A government hospital known to run a dengue ward in outbreak season.
 *
 * Deliberately carries no phone number and no hard-coded coordinate. Published
 * hospital numbers change often, and a wrong number during an emergency is
 * worse than no number; likewise a stale coordinate can send someone to the
 * wrong side of a city. The name and city are stable, so directions are
 * resolved live through whichever maps app the reader has.
 */
data class Hospital(
    val id: String,
    val name: String,
    val banglaName: String,
    val city: String,
    val banglaCity: String,
    val division: Division,
) {
    fun name(language: AppLanguage) = if (language == AppLanguage.BANGLA) banglaName else name
    fun city(language: AppLanguage) = if (language == AppLanguage.BANGLA) banglaCity else city

    /** What gets handed to the maps app for search and directions. */
    val mapQuery: String get() = "$name, $city, Bangladesh"
}

object HospitalDirectory {
    val all = listOf(
        // Dhaka
        Hospital("dmch", "Dhaka Medical College Hospital",
            "ঢাকা মেডিকেল কলেজ হাসপাতাল", "Dhaka", "ঢাকা", Division.DHAKA),
        Hospital("mitford", "Sir Salimullah Medical College Mitford Hospital",
            "স্যার সলিমুল্লাহ মেডিকেল কলেজ মিটফোর্ড হাসপাতাল", "Dhaka", "ঢাকা", Division.DHAKA),
        Hospital("suhrawardy", "Shaheed Suhrawardy Medical College Hospital",
            "শহীদ সোহরাওয়ার্দী মেডিকেল কলেজ হাসপাতাল", "Dhaka", "ঢাকা", Division.DHAKA),
        Hospital("mugda", "Mugda Medical College Hospital",
            "মুগদা মেডিকেল কলেজ হাসপাতাল", "Dhaka", "ঢাকা", Division.DHAKA),
        Hospital("kurmitola", "Kurmitola General Hospital",
            "কুর্মিটোলা জেনারেল হাসপাতাল", "Dhaka", "ঢাকা", Division.DHAKA),
        // Formerly BSMMU; renamed in 2024.
        Hospital("bsmmu", "Bangladesh Medical University",
            "বাংলাদেশ মেডিকেল বিশ্ববিদ্যালয়", "Shahbag, Dhaka", "শাহবাগ, ঢাকা", Division.DHAKA),
        Hospital("kuwait", "Kuwait Bangladesh Friendship Government Hospital",
            "কুয়েত বাংলাদেশ ফ্রেন্ডশিপ সরকারি হাসপাতাল", "Uttara, Dhaka", "উত্তরা, ঢাকা", Division.DHAKA),
        Hospital("shishu", "Dhaka Shishu (Children) Hospital",
            "ঢাকা শিশু হাসপাতাল", "Dhaka", "ঢাকা", Division.DHAKA),
        Hospital("mohanagar", "Mohanagar General Hospital",
            "মহানগর জেনারেল হাসপাতাল", "Dhaka", "ঢাকা", Division.DHAKA),
        Hospital("tajuddin", "Shaheed Tajuddin Ahmad Medical College Hospital",
            "শহীদ তাজউদ্দীন আহমদ মেডিকেল কলেজ হাসপাতাল", "Gazipur", "গাজীপুর", Division.DHAKA),
        Hospital("narayanganj", "Narayanganj General (Victoria) Hospital",
            "নারায়ণগঞ্জ জেনারেল (ভিক্টোরিয়া) হাসপাতাল", "Narayanganj", "নারায়ণগঞ্জ", Division.DHAKA),
        Hospital("faridpur", "Faridpur Medical College Hospital",
            "ফরিদপুর মেডিকেল কলেজ হাসপাতাল", "Faridpur", "ফরিদপুর", Division.DHAKA),

        // Chattogram
        Hospital("cmch", "Chattogram Medical College Hospital",
            "চট্টগ্রাম মেডিকেল কলেজ হাসপাতাল", "Chattogram", "চট্টগ্রাম", Division.CHATTOGRAM),
        Hospital("ctggeneral", "Chattogram General Hospital",
            "চট্টগ্রাম জেনারেল হাসপাতাল", "Chattogram", "চট্টগ্রাম", Division.CHATTOGRAM),
        Hospital("coxsbazar", "Cox's Bazar Medical College Hospital",
            "কক্সবাজার মেডিকেল কলেজ হাসপাতাল", "Cox's Bazar", "কক্সবাজার", Division.CHATTOGRAM),
        Hospital("cumilla", "Cumilla Medical College Hospital",
            "কুমিল্লা মেডিকেল কলেজ হাসপাতাল", "Cumilla", "কুমিল্লা", Division.CHATTOGRAM),
        Hospital("noakhali", "Abdul Malek Ukil Medical College Hospital",
            "আব্দুল মালেক উকিল মেডিকেল কলেজ হাসপাতাল", "Noakhali", "নোয়াখালী", Division.CHATTOGRAM),

        // Other divisions
        Hospital("rmch", "Rajshahi Medical College Hospital",
            "রাজশাহী মেডিকেল কলেজ হাসপাতাল", "Rajshahi", "রাজশাহী", Division.RAJSHAHI),
        Hospital("bogura", "Shaheed Ziaur Rahman Medical College Hospital",
            "শহীদ জিয়াউর রহমান মেডিকেল কলেজ হাসপাতাল", "Bogura", "বগুড়া", Division.RAJSHAHI),
        Hospital("kmch", "Khulna Medical College Hospital",
            "খুলনা মেডিকেল কলেজ হাসপাতাল", "Khulna", "খুলনা", Division.KHULNA),
        Hospital("jashore", "Jashore 250 Bed General Hospital",
            "যশোর ২৫০ শয্যা জেনারেল হাসপাতাল", "Jashore", "যশোর", Division.KHULNA),
        Hospital("osmani", "Sylhet MAG Osmani Medical College Hospital",
            "সিলেট এম এ জি ওসমানী মেডিকেল কলেজ হাসপাতাল", "Sylhet", "সিলেট", Division.SYLHET),
        Hospital("sherebangla", "Sher-e-Bangla Medical College Hospital",
            "শের-ই-বাংলা মেডিকেল কলেজ হাসপাতাল", "Barishal", "বরিশাল", Division.BARISHAL),
        Hospital("rangpur", "Rangpur Medical College Hospital",
            "রংপুর মেডিকেল কলেজ হাসপাতাল", "Rangpur", "রংপুর", Division.RANGPUR),
        Hospital("dinajpur", "Dinajpur M Abdur Rahim Medical College Hospital",
            "দিনাজপুর এম আব্দুর রহিম মেডিকেল কলেজ হাসপাতাল", "Dinajpur", "দিনাজপুর", Division.RANGPUR),
        Hospital("mmch", "Mymensingh Medical College Hospital",
            "ময়মনসিংহ মেডিকেল কলেজ হাসপাতাল", "Mymensingh", "ময়মনসিংহ", Division.MYMENSINGH),
    )

    fun hospitals(division: Division?): List<Hospital> =
        if (division == null) all else all.filter { it.division == division }
}

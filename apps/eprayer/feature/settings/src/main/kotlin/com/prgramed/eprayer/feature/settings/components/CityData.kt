package com.prgramed.eprayer.feature.settings.components

data class City(
    val name: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
) {
    val displayName: String get() = "$name, $country"
}

val cities = listOf(
    // Middle East & North Africa
    City("Mecca", "Saudi Arabia", 21.4225, 39.8262),
    City("Medina", "Saudi Arabia", 24.4672, 39.6024),
    City("Riyadh", "Saudi Arabia", 24.7136, 46.6753),
    City("Jeddah", "Saudi Arabia", 21.5433, 39.1728),
    City("Dammam", "Saudi Arabia", 26.3927, 49.9777),
    City("Dubai", "UAE", 25.2048, 55.2708),
    City("Abu Dhabi", "UAE", 24.4539, 54.3773),
    City("Sharjah", "UAE", 25.3463, 55.4209),
    City("Doha", "Qatar", 25.2854, 51.5310),
    City("Kuwait City", "Kuwait", 29.3759, 47.9774),
    City("Manama", "Bahrain", 26.2285, 50.5860),
    City("Muscat", "Oman", 23.5880, 58.3829),
    City("Amman", "Jordan", 31.9454, 35.9284),
    City("Jerusalem", "Palestine", 31.7683, 35.2137),
    City("Beirut", "Lebanon", 33.8938, 35.5018),
    City("Damascus", "Syria", 33.5138, 36.2765),
    City("Baghdad", "Iraq", 33.3153, 44.3661),
    City("Erbil", "Iraq", 36.1901, 44.0091),
    City("Tehran", "Iran", 35.6892, 51.3890),
    City("Isfahan", "Iran", 32.6546, 51.6680),
    City("Sanaa", "Yemen", 15.3694, 44.1910),
    City("Cairo", "Egypt", 30.0444, 31.2357),
    City("Alexandria", "Egypt", 31.2001, 29.9187),
    City("Tripoli", "Libya", 32.8872, 13.1913),
    City("Tunis", "Tunisia", 36.8065, 10.1815),
    City("Algiers", "Algeria", 36.7538, 3.0588),
    City("Rabat", "Morocco", 34.0209, -6.8416),
    City("Casablanca", "Morocco", 33.5731, -7.5898),

    // South & Southeast Asia
    City("Istanbul", "Turkey", 41.0082, 28.9784),
    City("Ankara", "Turkey", 39.9334, 32.8597),
    City("Islamabad", "Pakistan", 33.6844, 73.0479),
    City("Karachi", "Pakistan", 24.8607, 67.0011),
    City("Lahore", "Pakistan", 31.5204, 74.3587),
    City("Dhaka", "Bangladesh", 23.8103, 90.4125),
    City("Jakarta", "Indonesia", 6.2088, 106.8456),
    City("Kuala Lumpur", "Malaysia", 3.1390, 101.6869),
    City("Singapore", "Singapore", 1.3521, 103.8198),
    City("Kabul", "Afghanistan", 34.5553, 69.2075),
    City("New Delhi", "India", 28.6139, 77.2090),
    City("Mumbai", "India", 19.0760, 72.8777),
    City("Hyderabad", "India", 17.3850, 78.4867),

    // Sub-Saharan Africa
    City("Lagos", "Nigeria", 6.5244, 3.3792),
    City("Abuja", "Nigeria", 9.0579, 7.4951),
    City("Khartoum", "Sudan", 15.5007, 32.5599),
    City("Mogadishu", "Somalia", 2.0469, 45.3182),
    City("Addis Ababa", "Ethiopia", 8.9806, 38.7578),
    City("Nairobi", "Kenya", 1.2921, 36.8219),
    City("Dar es Salaam", "Tanzania", -6.7924, 39.2083),
    City("Dakar", "Senegal", 14.7167, -17.4677),
    City("Johannesburg", "South Africa", -26.2041, 28.0473),

    // Europe
    City("London", "UK", 51.5074, -0.1278),
    City("Birmingham", "UK", 52.4862, -1.8904),
    City("Paris", "France", 48.8566, 2.3522),
    City("Berlin", "Germany", 52.5200, 13.4050),
    City("Amsterdam", "Netherlands", 52.3676, 4.9041),
    City("Brussels", "Belgium", 50.8503, 4.3517),
    City("Stockholm", "Sweden", 59.3293, 18.0686),
    City("Oslo", "Norway", 59.9139, 10.7522),
    City("Copenhagen", "Denmark", 55.6761, 12.5683),
    City("Madrid", "Spain", 40.4168, -3.7038),
    City("Rome", "Italy", 41.9028, 12.4964),
    City("Vienna", "Austria", 48.2082, 16.3738),
    City("Athens", "Greece", 37.9838, 23.7275),
    City("Moscow", "Russia", 55.7558, 37.6173),
    City("Sarajevo", "Bosnia", 43.8563, 18.4131),
    City("Tirana", "Albania", 41.3275, 19.8187),

    // Americas
    City("New York", "USA", 40.7128, -74.0060),
    City("Los Angeles", "USA", 34.0522, -118.2437),
    City("Chicago", "USA", 41.8781, -87.6298),
    City("Houston", "USA", 29.7604, -95.3698),
    City("Dearborn", "USA", 42.3223, -83.1763),
    City("Toronto", "Canada", 43.6532, -79.3832),
    City("Montreal", "Canada", 45.5017, -73.5673),
    City("São Paulo", "Brazil", -23.5505, -46.6333),
    City("Buenos Aires", "Argentina", -34.6037, -58.3816),
    City("Mexico City", "Mexico", 19.4326, -99.1332),
    City("Bogota", "Colombia", 4.7110, -74.0721),

    // Central Asia
    City("Tashkent", "Uzbekistan", 41.2995, 69.2401),
    City("Astana", "Kazakhstan", 51.1694, 71.4491),
    City("Bishkek", "Kyrgyzstan", 42.8746, 74.5698),
    City("Dushanbe", "Tajikistan", 38.5598, 68.7740),
    City("Ashgabat", "Turkmenistan", 37.9601, 58.3261),
    City("Baku", "Azerbaijan", 40.4093, 49.8671),

    // East Asia & Oceania
    City("Beijing", "China", 39.9042, 116.4074),
    City("Tokyo", "Japan", 35.6762, 139.6503),
    City("Seoul", "South Korea", 37.5665, 126.9780),
    City("Sydney", "Australia", -33.8688, 151.2093),
    City("Melbourne", "Australia", -37.8136, 144.9631),
)

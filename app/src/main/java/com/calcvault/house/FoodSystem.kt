package com.calcvault.house

/**
 * FoodSystem.kt — Food items, feeding mechanics, and food mixer
 *
 * Provides a catalog of food items across categories,
 * a food mixer for combining foods, and fridge inventory.
 */

// ═══════════════════════════════════════════════════════════════════════════
// FOOD ITEMS
// ═══════════════════════════════════════════════════════════════════════════

enum class FoodCategory {
    FRUIT, MEAL, SNACK, DRINK, DESSERT, SMOOTHIE
}

data class FoodItem(
    val id: String,
    val name: String,
    val emoji: String,
    val category: FoodCategory,
    val hungerValue: Float,
    val happinessBoost: Float = 0f,
    val energyBoost: Float = 0f,
    val isMessing: Boolean = false,
    val cost: Int = 0,
    val description: String = ""
) {
    companion object {
        // ─── FRUITS ───
        val APPLE = FoodItem("apple", "Apple", "🍎", FoodCategory.FRUIT, 8f, 2f, description = "Crunchy and sweet")
        val BANANA = FoodItem("banana", "Banana", "🍌", FoodCategory.FRUIT, 7f, 2f, 1f, description = "Full of energy")
        val GRAPES = FoodItem("grapes", "Grapes", "🍇", FoodCategory.FRUIT, 5f, 3f, description = "Juicy clusters")
        val WATERMELON = FoodItem("watermelon", "Watermelon", "🍉", FoodCategory.FRUIT, 10f, 4f, description = "Summer treat")
        val STRAWBERRY = FoodItem("strawberry", "Strawberry", "🍓", FoodCategory.FRUIT, 6f, 5f, description = "Angela's favorite")
        val ORANGE = FoodItem("orange", "Orange", "🍊", FoodCategory.FRUIT, 7f, 2f, 2f, description = "Vitamin boost")

        // ─── MEALS ───
        val PIZZA = FoodItem("pizza", "Pizza", "🍕", FoodCategory.MEAL, 25f, 8f, isMessing = true, description = "Cheesy goodness")
        val BURGER = FoodItem("burger", "Burger", "🍔", FoodCategory.MEAL, 30f, 6f, isMessing = true, description = "Classic comfort food")
        val SPAGHETTI = FoodItem("spaghetti", "Spaghetti", "🍝", FoodCategory.MEAL, 28f, 7f, isMessing = true, description = "Lady and the Tramp style")
        val SUSHI = FoodItem("sushi", "Sushi", "🍣", FoodCategory.MEAL, 20f, 10f, description = "Elegant dining")
        val TACO = FoodItem("taco", "Taco", "🌮", FoodCategory.MEAL, 22f, 6f, isMessing = true, description = "Fiesta time")
        val CURRY = FoodItem("curry", "Curry", "🍛", FoodCategory.MEAL, 26f, 5f, description = "Warm and spicy")

        // ─── SNACKS ───
        val COOKIE = FoodItem("cookie", "Cookie", "🍪", FoodCategory.SNACK, 10f, 8f, description = "Freshly baked")
        val POPCORN = FoodItem("popcorn", "Popcorn", "🍿", FoodCategory.SNACK, 8f, 6f, description = "Movie night essential")
        val DONUT = FoodItem("donut", "Donut", "🍩", FoodCategory.SNACK, 12f, 10f, isMessing = true, description = "Sprinkle-covered")
        val CANDY = FoodItem("candy", "Candy", "🍬", FoodCategory.SNACK, 5f, 12f, description = "Sugar rush!")
        val ICE_CREAM = FoodItem("icecream", "Ice Cream", "🍦", FoodCategory.SNACK, 10f, 12f, isMessing = true, description = "Cool treat")
        val CAKE = FoodItem("cake", "Cake", "🎂", FoodCategory.DESSERT, 15f, 15f, isMessing = true, description = "Celebration cake!")

        // ─── DRINKS ───
        val MILK = FoodItem("milk", "Milk", "🥛", FoodCategory.DRINK, 8f, 2f, 5f, description = "Strong bones")
        val JUICE = FoodItem("juice", "Juice", "🧃", FoodCategory.DRINK, 6f, 3f, 4f, description = "Fresh squeezed")
        val COFFEE = FoodItem("coffee", "Coffee", "☕", FoodCategory.DRINK, 3f, 2f, 15f, description = "Wake up boost")
        val TEA = FoodItem("tea", "Tea", "🍵", FoodCategory.DRINK, 2f, 5f, 8f, description = "Calming brew")
        val WATER = FoodItem("water", "Water", "💧", FoodCategory.DRINK, 1f, 1f, 3f, description = "Stay hydrated")

        val ALL_FOODS = listOf(
            APPLE, BANANA, GRAPES, WATERMELON, STRAWBERRY, ORANGE,
            PIZZA, BURGER, SPAGHETTI, SUSHI, TACO, CURRY,
            COOKIE, POPCORN, DONUT, CANDY, ICE_CREAM, CAKE,
            MILK, JUICE, COFFEE, TEA, WATER
        )

        fun getByCategory(category: FoodCategory): List<FoodItem> =
            ALL_FOODS.filter { it.category == category }

        fun getById(id: String): FoodItem? =
            ALL_FOODS.firstOrNull { it.id == id }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// FOOD MIXER
// ═══════════════════════════════════════════════════════════════════════════

object FoodMixer {

    data class SmoothieResult(
        val name: String,
        val emoji: String,
        val food: FoodItem,
        val reactionEmoji: String // Character's reaction
    )

    private val specialCombinations = mapOf(
        setOf("apple", "banana") to SmoothieResult(
            "Tropical Blast", "🥤",
            FoodItem("smoothie_tropical", "Tropical Blast", "🥤", FoodCategory.SMOOTHIE, 18f, 8f, 3f, description = "Fruity paradise"),
            "😍"
        ),
        setOf("strawberry", "milk") to SmoothieResult(
            "Strawberry Shake", "🥤",
            FoodItem("smoothie_strawberry", "Strawberry Shake", "🥤", FoodCategory.SMOOTHIE, 16f, 12f, 4f, description = "Angela's special"),
            "🤩"
        ),
        setOf("coffee", "milk") to SmoothieResult(
            "Latte", "☕",
            FoodItem("latte", "Latte", "☕", FoodCategory.SMOOTHIE, 5f, 6f, 18f, description = "Smooth caffeine"),
            "😊"
        ),
        setOf("cookie", "milk") to SmoothieResult(
            "Cookie Milkshake", "🥤",
            FoodItem("cookie_shake", "Cookie Milkshake", "🥤", FoodCategory.SMOOTHIE, 18f, 15f, 2f, description = "Cookies & cream"),
            "🤤"
        ),
        setOf("watermelon", "ice_cream") to SmoothieResult(
            "Summer Frost", "🧊",
            FoodItem("summer_frost", "Summer Frost", "🧊", FoodCategory.SMOOTHIE, 15f, 14f, 2f, description = "Frozen delight"),
            "😎"
        )
    )

    fun mix(food1: FoodItem, food2: FoodItem): SmoothieResult {
        val key = setOf(food1.id, food2.id)

        // Check for special combination
        specialCombinations[key]?.let { return it }

        // Generic smoothie from any two items
        val combinedHunger = (food1.hungerValue + food2.hungerValue) * 0.8f
        val combinedHappiness = (food1.happinessBoost + food2.happinessBoost) * 1.1f
        val combinedEnergy = food1.energyBoost + food2.energyBoost

        val resultFood = FoodItem(
            id = "smoothie_${food1.id}_${food2.id}",
            name = "${food1.name} & ${food2.name} Mix",
            emoji = "🥤",
            category = FoodCategory.SMOOTHIE,
            hungerValue = combinedHunger,
            happinessBoost = combinedHappiness,
            energyBoost = combinedEnergy,
            description = "A creative blend!"
        )

        return SmoothieResult(
            name = resultFood.name,
            emoji = "${food1.emoji}+${food2.emoji}",
            food = resultFood,
            reactionEmoji = if (combinedHappiness > 10f) "😋" else "🤔"
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// FRIDGE INVENTORY
// ═══════════════════════════════════════════════════════════════════════════

class FridgeInventory {
    private val inventory = mutableMapOf<String, Int>() // foodId -> quantity
    private val maxCapacity = 20

    init {
        // Start with some basic food
        inventory["apple"] = 3
        inventory["milk"] = 2
        inventory["cookie"] = 2
        inventory["water"] = 5
        inventory["pizza"] = 1
    }

    fun getContents(): Map<String, Int> = inventory.toMap()

    fun getAvailableFoods(): List<Pair<FoodItem, Int>> =
        inventory.filter { it.value > 0 }
            .mapNotNull { (id, qty) -> FoodItem.getById(id)?.let { it to qty } }

    fun takeFood(foodId: String): FoodItem? {
        val qty = inventory[foodId] ?: return null
        if (qty <= 0) return null
        inventory[foodId] = qty - 1
        if (inventory[foodId] == 0) inventory.remove(foodId)
        return FoodItem.getById(foodId)
    }

    fun addFood(foodId: String, quantity: Int = 1): Boolean {
        val total = inventory.values.sum()
        if (total + quantity > maxCapacity) return false
        inventory[foodId] = (inventory[foodId] ?: 0) + quantity
        return true
    }

    fun getTotalItems(): Int = inventory.values.sum()

    fun isFull(): Boolean = getTotalItems() >= maxCapacity

    fun isEmpty(): Boolean = inventory.isEmpty() || inventory.values.all { it == 0 }
}

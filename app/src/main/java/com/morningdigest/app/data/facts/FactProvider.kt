package com.morningdigest.app.data.facts

import com.morningdigest.app.data.model.DailyFact
import kotlin.random.Random

/**
 * A small offline pool of one-paragraph "fact of the day" entries, grouped by
 * category. Kept fully offline (no extra API/network dependency) since the
 * fact is cosmetic and shouldn't be able to fail the digest refresh. A new
 * one is picked at random every time [buildFreshReport] runs, so it changes
 * on every manual refresh as well as on the scheduled morning run.
 *
 * Deliberately avoids the handful of facts that show up in literally every
 * "did you know" list ever printed (honey never spoiling, octopuses having
 * three hearts, a day on Venus being longer than its year, etc.) - those get
 * old fast precisely because everyone's already heard them. The pool below
 * leans toward more specific, less-recycled trivia across a wider spread of
 * categories, so repeats feel less likely and each one still lands.
 */
object FactProvider {

    private val facts: List<DailyFact> = listOf(
        // --- Business ---
        DailyFact(
            "Business",
            "In 1975, Ray Kroc's McDonald's was making more money from real estate than from selling hamburgers - the company bought the land under its franchises and leased it back to owners, turning fast food into one of the largest landlords in the world."
        ),
        DailyFact(
            "Business",
            "LEGO once nearly went bankrupt in 2003 despite selling toys - the fix wasn't a new product line but ruthless focus, cutting its part catalog from over 12,000 unique pieces down to about 7,000 to simplify manufacturing."
        ),
        DailyFact(
            "Business",
            "The first-ever product with a barcode scanned at a real cash register was a pack of Wrigley's chewing gum, rung up at a supermarket in Ohio in 1974; that pack is now kept in the Smithsonian."
        ),
        DailyFact(
            "Business",
            "Kodak invented the first digital camera in 1975, then largely shelved it internally for years out of fear it would cannibalize its own film business - a hesitation historians now point to as a textbook case of a company being undone by its own past success."
        ),
        DailyFact(
            "Business",
            "IKEA's product names aren't random - sofas and armchairs get Swedish place names, while bookcases are typically named after professions, a naming system the founder set up specifically because he was dyslexic and struggled with plain model numbers."
        ),

        // --- History ---
        DailyFact(
            "History",
            "The Great Fire of London in 1666 destroyed most of the medieval city, yet official records list only a handful of confirmed deaths - historians believe the true toll was far higher but went uncounted among the poor."
        ),
        DailyFact(
            "History",
            "Oxford University is older than the Aztec Empire - teaching there had already been going on for over a century before the Aztecs founded their capital city of Tenochtitlan in 1325."
        ),
        DailyFact(
            "History",
            "Napoleon Bonaparte was once attacked by a swarm of rabbits during a supposedly friendly hunting event organized by his own staff, after the rabbits - bred in captivity - charged the humans instead of fleeing them."
        ),
        DailyFact(
            "History",
            "The shortest war on record lasted well under an hour - a dispute between Britain and Zanzibar in 1896 was over before most of the fighting had even properly started, once the Sultan's palace was shelled into surrender."
        ),
        DailyFact(
            "History",
            "Cleopatra lived closer in time to the invention of the smartphone than to the construction of the Great Pyramid of Giza, which was already about 2,500 years old by the time she was born."
        ),

        // --- AI & Technology ---
        DailyFact(
            "AI",
            "The term \"artificial intelligence\" was coined in 1956 at a summer workshop at Dartmouth College, where researchers optimistically believed a few months of focused work could make real progress on machine intelligence."
        ),
        DailyFact(
            "AI",
            "Early machine translation research in the 1950s was so overconfident that a famous demo translated Russian to English with just 250 words and six grammar rules, giving the false impression that full translation was nearly solved."
        ),
        DailyFact(
            "AI",
            "The game of Go was considered a major milestone for AI not because of its rules, which are simple, but because the number of possible board positions is estimated to exceed the number of atoms in the observable universe."
        ),
        DailyFact(
            "Technology",
            "The first computer bug was, quite literally, a bug - in 1947 engineers working on the Harvard Mark II found a moth trapped in a relay causing a malfunction, taped it into the logbook, and the term stuck around long after."
        ),
        DailyFact(
            "Technology",
            "The QWERTY keyboard layout wasn't designed for typing speed - it was arranged in the 1870s partly to keep frequently-paired mechanical typewriter arms apart so they wouldn't jam, and it simply never got replaced."
        ),

        // --- Space ---
        DailyFact(
            "Space",
            "Neutron stars are so dense that a teaspoon of their material would weigh roughly as much as a mountain here on Earth - they form when a massive star's core collapses in on itself after a supernova."
        ),
        DailyFact(
            "Space",
            "There is a giant cloud of alcohol drifting in a star-forming region of the Milky Way roughly 10,000 light-years away, containing enough ethyl alcohol to fill trillions of trillions of pints - though it's far too diffuse to ever drink."
        ),
        DailyFact(
            "Space",
            "The Voyager 1 probe, launched in 1977, is now over 15 billion miles from Earth and still sending back data - its radio signals take more than 20 hours to reach mission control."
        ),
        DailyFact(
            "Space",
            "Saturn's largest moon, Titan, has rivers, lakes, and rain - just made of liquid methane and ethane instead of water, since surface temperatures there hover around minus 179°C."
        ),

        // --- Language ---
        DailyFact(
            "Language",
            "The longest place name still in official use belongs to a hill in New Zealand: Taumatawhakatangihangakoauauotamateaturipukakapikimaungahoronukupokaiwhenuakitanatahu, a Māori name roughly 85 letters long."
        ),
        DailyFact(
            "Language",
            "\"Nieces\" and \"nephews\" collectively have a single word in English that almost nobody uses - \"niblings\" - coined by linguists in the 1950s as a gender-neutral shortcut that never really caught on outside niche circles."
        ),
        DailyFact(
            "Language",
            "The word \"set\" has more distinct dictionary definitions in English than any other word - the Oxford English Dictionary lists several hundred separate senses for it, edging out even \"run\" and \"go.\""
        ),

        // --- Human Body & Health ---
        DailyFact(
            "Human Body",
            "Human eyes can distinguish an estimated 10 million distinct colors, thanks to three types of cone cells in the retina - a rare genetic condition called tetrachromacy gives some people a fourth cone type and, reportedly, a noticeably richer color range."
        ),
        DailyFact(
            "Human Body",
            "The \"butterflies\" feeling in your stomach before something nerve-wracking is a real physiological stress response - blood gets redirected away from digestion toward muscles preparing for action, which is what you're actually feeling."
        ),
        DailyFact(
            "Human Body",
            "Your sense of smell is directly wired to the part of the brain that handles memory and emotion, which is why a specific scent can trigger an old memory far more vividly than a photo of the same moment ever could."
        ),

        // --- Geography ---
        DailyFact(
            "Geography",
            "Africa is large enough that the United States, China, India, Japan, and most of Europe could all fit inside its borders at once with room to spare - a scale that's easy to underestimate on most standard world maps."
        ),
        DailyFact(
            "Geography",
            "Russia spans 11 time zones, more than any other country - it's technically Tuesday afternoon in the far east of the country while it's still Tuesday morning in Moscow, thousands of kilometers to the west."
        ),
        DailyFact(
            "Geography",
            "There's a US-Canada border town, Derby Line, where the public library sits directly on the international boundary line - patrons on one side of a reading room are technically in a different country than patrons on the other."
        ),

        // --- Food ---
        DailyFact(
            "Food",
            "Carrots were originally purple, not orange - the now-familiar orange carrot is widely believed to have been selectively bred in the Netherlands around the 17th century, according to popular horticultural history."
        ),
        DailyFact(
            "Food",
            "Wasabi served at most sushi restaurants outside Japan isn't actually wasabi - genuine wasabi root is notoriously hard to grow, so most of what's served is dyed horseradish paste standing in for the real thing."
        ),
        DailyFact(
            "Food",
            "A pineapple takes around two years to grow from planting to ripe fruit, which is part of why it was historically such a status symbol - 18th-century hosts sometimes rented pineapples purely as a dinner-party centerpiece."
        ),

        // --- Animals ---
        DailyFact(
            "Animals",
            "Crows can recognize individual human faces and hold onto that memory for years, reportedly even passing along a grudge against a specific person to other crows that never personally encountered them."
        ),
        DailyFact(
            "Animals",
            "A group of flamingos will often synchronize into one giant slow-motion dance during courtship season, a display researchers believe helps individuals size up potential partners across the whole group at once."
        ),
        DailyFact(
            "Animals",
            "The mantis shrimp can throw a punch so fast it briefly boils the water around it, creating a flash of light and a shockwave strong enough to crack aquarium glass - all from an animal usually just a few centimeters long."
        ),

        // --- Sports & Records ---
        DailyFact(
            "Sports",
            "Jousting is the official state sport of Maryland, a designation it's held since 1962, even though it's a niche hobby there today compared to more mainstream American sports."
        ),
        DailyFact(
            "Sports",
            "The oldest known Olympic gold medalist was a Swedish shooter who won in the 1912 Games at age 64 - a record that's stood for over a century and shows no sign of being challenged soon."
        ),

        // --- Economics ---
        DailyFact(
            "Economics",
            "During a shortage of small coins in the 1700s, some businesses issued their own local currency called \"trade tokens,\" a practice that briefly returned in the 1970s U.S. coin shortage."
        ),
        DailyFact(
            "Economics",
            "Iceland's entire fishing-dependent economy nearly collapsed in 2008 when its three main banks, which had grown to roughly ten times the size of the national economy, all failed within the same week."
        ),
        DailyFact(
            "Economics",
            "The \"Big Mac Index,\" invented by The Economist in 1986 as a lighthearted way to compare currencies, is still used informally today as a rough gauge of whether a currency is over- or under-valued."
        ),

        // --- Ocean ---
        DailyFact(
            "Ocean",
            "More of the Moon's surface has been mapped in detail than the floor of Earth's own oceans - well over 80% of the deep sea remains unmapped at high resolution."
        ),
        DailyFact(
            "Ocean",
            "The blue whale's heart is roughly the size of a small car, and its heartbeat can reportedly be detected from over a mile away underwater during deep dives."
        ),
        DailyFact(
            "Ocean",
            "There's an underwater lake inside the Gulf of Mexico - a dense pool of brine that sits on the seafloor without mixing into the surrounding ocean, dense enough that submersibles can float on its surface."
        ),

        // --- Music ---
        DailyFact(
            "Music",
            "The world's longest-running live musical performance is a John Cage organ piece in Germany designed to last 639 years total - it's still playing, with the next note change scheduled decades from now."
        ),
        DailyFact(
            "Music",
            "The clarinet-like opening glissando in Gershwin's \"Rhapsody in Blue\" was reportedly a joke a clarinetist played on Gershwin during rehearsal - he liked it enough to keep it in the final score."
        ),
        DailyFact(
            "Music",
            "Vinyl records and CDs both work by encoding sound as physical shape - one as a groove a needle traces, the other as microscopic pits read by a laser - yet a single CD can hold what would need dozens of vinyl records at higher fidelity."
        ),

        // --- Numbers & Math ---
        DailyFact(
            "Numbers",
            "Zero as a standalone number, rather than just a placeholder, took centuries to catch on - ancient Rome had no symbol for it at all, which is part of why Roman numeral arithmetic was so cumbersome."
        ),
        DailyFact(
            "Numbers",
            "A googol - the number 1 followed by 100 zeroes - is larger than the estimated number of atoms in the observable universe, even though it's just a made-up word a mathematician's nephew coined in 1920."
        ),
        DailyFact(
            "Numbers",
            "Benford's Law observes that in most real-world sets of numbers - populations, invoice amounts, river lengths - the digit 1 shows up as the leading digit far more often than 9, a pattern accountants now use to help spot fraud."
        ),

        // --- Ancient Civilizations ---
        DailyFact(
            "History",
            "The Antikythera mechanism, recovered from a Greek shipwreck over a century ago, turned out to be a hand-cranked bronze device for predicting eclipses and planetary positions - roughly 2,000 years before anything comparably complex was built again."
        ),
        DailyFact(
            "History",
            "Ancient Roman concrete used in structures like the Pantheon has proven more durable in some cases than modern concrete, partly because a chemical reaction with seawater in certain mixes actually strengthens it over centuries instead of eroding it."
        ),

        // --- Psychology & Human Behavior ---
        DailyFact(
            "Psychology",
            "People tend to remember unfinished or interrupted tasks better than completed ones, a pattern named the Zeigarnik effect after the psychologist who noticed waiters recalled unpaid orders far better than ones already settled."
        ),
        DailyFact(
            "Psychology",
            "The \"mere exposure effect\" describes how simply encountering something repeatedly - a song, a face, a logo - tends to make people like it more, even with zero other information about it."
        )
    )

    /**
     * Picks a random fact, changing every time this is called (e.g. on every
     * refresh). [excludeTexts] should be whatever facts have shown up
     * recently (not just the last one) - skipping all of them where
     * possible is what actually stops the small pool from feeling
     * repetitive on frequent refreshes. Falls back to the full pool if
     * excluding them all would leave nothing to pick from.
     */
    fun randomFact(excludeTexts: Set<String> = emptySet()): DailyFact {
        val candidates = facts.filterNot { it.text in excludeTexts }
        val pool = candidates.ifEmpty { facts }
        return pool[Random.nextInt(pool.size)]
    }
}

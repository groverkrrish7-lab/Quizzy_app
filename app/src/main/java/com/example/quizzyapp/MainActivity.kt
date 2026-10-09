package com.example.quizzyapp

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

// ---------------- Settings you can change ----------------
private const val QUESTIONS_PER_QUIZ = 10     // how many questions in one quiz
private const val TIMER_ENABLED = true        // false = no timer
private const val SECONDS_PER_QUESTION = 20   // time for each question
private const val HURRAY_PERCENT = 100        // "Yay!" popup appears only at a perfect score
private const val MAX_POINTS = 100            // points for a correct answer given instantly
private const val MIN_POINTS = 50             // points for a correct answer given at the last second

// One question. answer = index of the correct option (0 = first, 1 = second, ...)
private data class Q(
    val text: String,
    val options: List<String>,
    val answer: Int,
    val explanation: String = ""
)

// ---------------- Question bank (add your own lines here) ----------------
private val bank = listOf(
    // your original 4 questions
    Q("Which language is used for Android development?", listOf("Java", "Kotlin", "Python", "C++"), 1, "Kotlin is Google's preferred language for Android."),
    Q("Which IDE is commonly used for Android development?", listOf("Android Studio", "IntelliJ", "Visual Studio", "Eclipse"), 0, "Android Studio is the official Android IDE."),
    Q("What does APK stands for", listOf("Android Package Kit", "Android Project Kit", "Android Package File", "Android Project File"), 0, "APK stands for Android Package Kit."),
    Q("Which data structure follow FIFO", listOf("Stack", "Queue", "Tree", "Linked List"), 1, "FIFO = First In, First Out, like a line of people."),

    // more Android
    Q("Which file declares an app's permissions?", listOf("build.gradle", "strings.xml", "AndroidManifest.xml", "MainActivity.kt"), 2, "Permissions go in AndroidManifest.xml."),
    Q("Which Kotlin keyword declares a read-only variable?", listOf("var", "val", "let", "const"), 1, "val cannot be reassigned."),
    Q("Which Activity method runs first when a screen is created?", listOf("onStart", "onResume", "onCreate", "onDestroy"), 2, "onCreate is where you set up the screen."),

    // programming
    Q("Which data structure follows LIFO?", listOf("Queue", "Array", "Stack", "Graph"), 2, "LIFO = Last In, First Out, like a pile of plates."),
    Q("What is the binary form of the number 5?", listOf("110", "101", "011", "111"), 1, "5 = 4 + 1, which is 101."),
    Q("What is the time complexity of binary search?", listOf("O(n)", "O(log n)", "O(n squared)", "O(1)"), 1, "It halves the search range every step."),
    Q("What does HTML stand for?", listOf("HyperText Markup Language", "High Tech Modern Language", "Hyper Transfer Markup Logic", "Home Tool Markup Language"), 0, "HTML is HyperText Markup Language."),
    Q("Which symbol starts a single-line comment in Kotlin?", listOf("#", "//", "--", "<!--"), 1, "Kotlin and Java use // for one-line comments."),

    // general knowledge
    Q("What is the capital of India?", listOf("Mumbai", "New Delhi", "Kolkata", "Chennai"), 1, "New Delhi is the capital of India."),
    Q("Which is the largest ocean on Earth?", listOf("Atlantic", "Indian", "Pacific", "Arctic"), 2, "The Pacific covers about one third of the planet."),
    Q("Who wrote the Indian national anthem?", listOf("Bankim Chandra Chatterjee", "Rabindranath Tagore", "Sarojini Naidu", "Subhash Chandra Bose"), 1, "Jana Gana Mana was written by Rabindranath Tagore."),
    Q("How many continents are there?", listOf("5", "6", "7", "8"), 2, "Asia, Africa, North America, South America, Antarctica, Europe, Australia."),
    Q("What is the currency of Japan?", listOf("Yuan", "Won", "Yen", "Ringgit"), 2, "The Japanese yen."),
    Q("Who was the first Prime Minister of India?", listOf("Jawaharlal Nehru", "Mahatma Gandhi", "Sardar Patel", "Lal Bahadur Shastri"), 0, "Jawaharlal Nehru served from 1947 to 1964."),

    // science
    Q("Which is the largest planet in our solar system?", listOf("Saturn", "Jupiter", "Neptune", "Earth"), 1, "Jupiter is more than 11 times wider than Earth."),
    Q("What is the chemical formula of water?", listOf("CO2", "O2", "H2O", "NaCl"), 2, "Two hydrogen atoms and one oxygen atom."),
    Q("Which gas do plants absorb for photosynthesis?", listOf("Oxygen", "Nitrogen", "Carbon dioxide", "Hydrogen"), 2, "Plants use CO2, water and sunlight to make food."),
    Q("What is the powerhouse of the cell?", listOf("Nucleus", "Mitochondria", "Ribosome", "Vacuole"), 1, "Mitochondria produce most of the cell's energy."),
    Q("What is the hardest natural substance?", listOf("Gold", "Iron", "Quartz", "Diamond"), 3, "Diamond is the hardest natural material."),
    Q("Approximately how fast does light travel?", listOf("3,000 km/s", "30,000 km/s", "300,000 km/s", "3,000,000 km/s"), 2, "About 300,000 kilometres per second.")
)

class MainActivity : ComponentActivity() {

    private lateinit var questionText: TextView
    private lateinit var questionNumberText: TextView
    private lateinit var optionsGroup: RadioGroup
    private lateinit var nextButton: Button
    private lateinit var radioButtons: Array<RadioButton>
    private lateinit var prefs: SharedPreferences

    private var quiz = listOf<Q>()
    private var currentQuestion = 0
    private var score = 0                      // number of correct answers
    private var points = 0                     // speed-based points
    private var questionStartTime = 0L
    private var fiftyUsed = false
    private var timer: CountDownTimer? = null
    private val review = ArrayList<String>()   // wrong answers, sent to the result screen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        questionText = findViewById(R.id.questionText)
        questionNumberText = findViewById(R.id.questionNumberText)
        optionsGroup = findViewById(R.id.optionsGroup)
        nextButton = findViewById(R.id.nextButton)
        radioButtons = arrayOf(
            findViewById(R.id.option1),
            findViewById(R.id.option2),
            findViewById(R.id.option3),
            findViewById(R.id.option4)
        )
        prefs = getSharedPreferences("quizzy_prefs", Context.MODE_PRIVATE)

        // Long-press the question for a 50/50 hint (once per quiz)
        questionText.setOnLongClickListener {
            useFifty()
            true
        }

        nextButton.setOnClickListener { onNextClicked() }

        // Ask before quitting in the middle of a quiz
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Quit quiz?")
                    .setMessage("Your progress will be lost.")
                    .setPositiveButton("Quit") { _, _ -> finish() }
                    .setNegativeButton("Keep playing", null)
                    .show()
            }
        })

        startQuiz()
        toast("Tip: long-press the question for a 50/50 hint")
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    // ---------------- quiz flow ----------------
    private fun startQuiz() {
        // random questions, and the answer order is shuffled too
        quiz = bank.shuffled().take(QUESTIONS_PER_QUIZ).map { q ->
            val correct = q.options[q.answer]
            val shuffled = q.options.shuffled()
            q.copy(options = shuffled, answer = shuffled.indexOf(correct))
        }
        currentQuestion = 0
        score = 0
        points = 0
        fiftyUsed = false
        review.clear()
        showQuestion()
    }

    private fun showQuestion() {
        timer?.cancel()
        val q = quiz[currentQuestion]

        questionText.text = q.text
        questionNumberText.text = "${currentQuestion + 1}/${quiz.size}"
        nextButton.text = if (currentQuestion == quiz.size - 1) "Finish" else "Next"

        for (i in radioButtons.indices) {
            radioButtons[i].text = q.options[i]
            radioButtons[i].isEnabled = true
            radioButtons[i].alpha = 1f
        }
        optionsGroup.clearCheck()

        questionStartTime = SystemClock.elapsedRealtime()
        startTimer()
    }

    private fun startTimer() {
        if (!TIMER_ENABLED) return
        val label = "${currentQuestion + 1}/${quiz.size}"
        timer = object : CountDownTimer(SECONDS_PER_QUESTION * 1000L, 1000) {
            override fun onTick(ms: Long) {
                questionNumberText.text = "$label  •  ${(ms + 999) / 1000}s"
            }

            override fun onFinish() {
                evaluate(-1, timedOut = true)
            }
        }.start()
    }

    private fun onNextClicked() {
        val selectedId = optionsGroup.checkedRadioButtonId
        if (selectedId == -1) {
            toast("Select an Answer")
            return
        }
        evaluate(radioButtons.indexOfFirst { it.id == selectedId })
    }

    // selectedIndex = -1 means no answer (time ran out)
    private fun evaluate(selectedIndex: Int, timedOut: Boolean = false) {
        timer?.cancel()
        val q = quiz[currentQuestion]
        val correctText = q.options[q.answer]

        if (selectedIndex == q.answer) {
            score++
            // faster answer = more points (MAX_POINTS if instant, MIN_POINTS at the last second)
            val seconds = (SystemClock.elapsedRealtime() - questionStartTime) / 1000.0
            val speed = (1.0 - seconds / SECONDS_PER_QUESTION).coerceIn(0.0, 1.0)
            val earned = MIN_POINTS + ((MAX_POINTS - MIN_POINTS) * speed).toInt()
            points += earned
            toast("Correct 👏  +$earned points")
        } else {
            toast(
                if (timedOut) "Time's up! Correct Answer: $correctText"
                else "Wrong, Correct Answer: $correctText"
            )
            val yours = if (selectedIndex >= 0) q.options[selectedIndex] else "No answer"
            review.add(
                "${q.text}\nYour answer: $yours\nCorrect answer: $correctText" +
                        if (q.explanation.isNotBlank()) "\n${q.explanation}" else ""
            )
        }

        if (currentQuestion < quiz.size - 1) {
            currentQuestion++
            showQuestion()
        } else {
            openResultScreen()
        }
    }

    private fun useFifty() {
        if (fiftyUsed) {
            toast("50/50 already used in this quiz")
            return
        }
        fiftyUsed = true
        val q = quiz[currentQuestion]
        q.options.indices
            .filter { it != q.answer }
            .shuffled()
            .take(2)
            .forEach {
                if (radioButtons[it].isChecked) optionsGroup.clearCheck()
                radioButtons[it].isEnabled = false
                radioButtons[it].alpha = 0.3f
            }
        toast("50/50: two wrong answers removed")
    }

    private fun openResultScreen() {
        timer?.cancel()

        // remember the best score (as a percentage)
        val percent = score * 100 / quiz.size
        val oldBest = prefs.getInt("best_percent", -1)
        val isNewBest = percent > oldBest
        if (isNewBest) prefs.edit().putInt("best_percent", percent).apply()

        // remember the best speed points too
        val maxPoints = quiz.size * MAX_POINTS
        val oldBestPoints = prefs.getInt("best_points", -1)
        val isNewBestPoints = points > oldBestPoints
        if (isNewBestPoints) prefs.edit().putInt("best_points", points).apply()

        val intent = Intent(this, ResultActivity::class.java)
        intent.putExtra("SCORE", score)
        intent.putExtra("TOTAL", quiz.size)
        // extra info your ResultActivity can use if you want:
        intent.putExtra("BEST", maxOf(percent, oldBest))
        intent.putExtra("NEW_BEST", isNewBest)
        intent.putExtra("POINTS", points)
        intent.putExtra("MAX_POINTS", maxPoints)
        intent.putExtra("BEST_POINTS", maxOf(points, oldBestPoints))
        intent.putStringArrayListExtra("REVIEW", review)

        val perfect = percent >= HURRAY_PERCENT
        if (perfect) celebrate()

        val message = (if (perfect) "Perfect score!\n" else "") +
                "Correct answers: $score/${quiz.size}\n" +
                "Points: $points / $maxPoints\n" +
                "Best points: ${maxOf(points, oldBestPoints)}" +
                if (isNewBestPoints) "\n🏆 New best!" else ""

        AlertDialog.Builder(this)
            .setTitle(if (perfect) "Yay! 🎉" else "Quiz finished")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("See result") { _, _ ->
                startActivity(intent)
                finish()
            }
            .show()
    }

    // short happy beep (no sound file needed)
    private fun celebrate() {
        try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 80)
                .startTone(ToneGenerator.TONE_PROP_ACK, 400)
        } catch (_: Exception) { }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
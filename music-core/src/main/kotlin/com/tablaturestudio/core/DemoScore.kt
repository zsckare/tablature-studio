package com.tablaturestudio.core

/** EN: Offline fixture for the first three sprints. ES: Ejemplo local para los tres primeros sprints. */
object DemoScore {
    fun create(): Score = Score("First Riff", "Tablature Studio", listOf(
        Track("Lead Guitar", listOf(64, 59, 55, 50, 45, 40), (1..8).map { measure ->
            Measure(measure, (0..7).map { step ->
                Beat(Duration(1, 8), listOf(Note(if (step % 3 == 0) 2 else 1, (step + measure) % 9)))
            })
        }),
        Track("Bass", listOf(43, 38, 33, 28), (1..8).map { measure ->
            Measure(measure, (0..3).map { step -> Beat(Duration(1, 4), listOf(Note(4, (measure + step) % 5))) })
        })
    ))
}

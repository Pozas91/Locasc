## Limits to terminate execution

Se pueden definir varias condiciones de parada:

- Fixed Generation
    * Termina la ejecución en el número máximo de generaciones establecido.
- Steady fitness
    * Termina la ejecución cuando el mejor fitness no cambia después del número indicado (no necesariamente seguido).
- Evolution time
    * Termina la ejecución cuando se alcanza el tiempo indicado.
- Fitness threshold
    * Termina la ejecución cuando el mejor fitness de la población actual es mayor al umbral indicado
      (para problemas de maximización)
- Fitness convergence
    * Termina cuando el fitness es considerado convergente. Utiliza dos filtros usados para suavizar el mejor fitness a
      lo largo de las generaciones. Cuando el mejor fitness suavizado del filtro alto está a menos de un porcentaje
      especificado del mejor fitness suavizado del filtro corto, entonces el fitness se considera convergente. P.e.
      Filtro corto = 10, Filtro largo = 30, Epsilon = 0.1 -> En este caso cogerá la media de las últimas 30 generaciones
      y la media de las 10 últimas generaciones, y comprobará que la diferencia de estas no supera el 10%, es decir, que
      no ha habido una mejora de la media sustancial en las 10 últimas generaciones.
- Population convergence
    * Termina cuando se considera que la población ha convergido. Una población es considerada convergente cuando el
      fitness medio de la población actual es menor que el especificado por el usuario del mejor fitness de la actual
      población. p.e. byPopulationConvergence(0.1) -> terminará si la diferencia entre el fitness medio de la población
      y el fitness máximo de la población es mejor que el 10%.
- Gene convergence
    * Termina cuando el porcentaje indicado de genes ha convergido. Se dice que un gen ha convergido cuando el valor
      promedio de ese gen en todos los genotipos de la población actual es mejor que el porcentaje dado del valor máximo
      de los alelos.

## Selectors

Los selectores son responsables de seleccionar un número de individuos de la población. Los selectores son usados para
dividir la población en supervivientes y descendientes.

- TournamentSelector
    * Un individuo ganará un torneo solo si el fitness es mayor que el fitness de los otros individuos competidores. Se
      puede ajustar el número de individuos a comparar. Para largos valores de `s`, los individuos débiles tienen menos
      probabilidad de ser seleccionados.
- TruncationSelector
    * La selección de individuos son ordenados de acuerdo a sus valores y solo los `n` mejores son seleccionados.
- MonteCarloSelector
    * Se selecciona los individuos de una forma aleatoria. Este selector puede ser usado para medir el rendimiento de
      los otros selectores.
- ProbabilitySelector
    * Son una variación del selector de fitness proporcional y selecciona individuos de una población basada en la
      selección probabilística.
- RouletteWheelSelector
    * El selector ruleta es también conocido como el selector proporcional del fitness y Jenetics lo implementa como un
      selector de probabilidad. La población no ha sido ordenada ante de seleccionar los individuos.
- LinearRankSelector
    * El selector ruleta tendrá problemas cuando los valores fitness difieren mucho. Si el mejor fitness del cromosoma
      es 90%, su circunferencia ocupa el 90% de la ruleta, y entonces otros cromosomas tienen menos oportunidades de ser
      seleccionado. En la selección linear-ranking los individuos son ordenados de acuerdo a sus valores de ajuste.
- ExponentialRankSelector
    * Una alternativa para la debilidad del selector linear-rank es asignar probabilidades de supervivencia para ordenar
      los individuos usando la función exponencial.
- BoltzmannSelector
    * Usa la selección de Boltzmann (Tendría que investigarlo)
- StochasticUniversalSelector
    * Es un método que selecciona individuos de acuerdo a una probabilidad dada de una forma que minimiza la
      probabilidad de fluctuaciones. Puede ser visto como un tipo de juego de la ruleta donde tenemos 'p' puntos
      igualmente espaciados que giran.
- EliteSelector
    * Copia una porción de los candidatos, sin cambiarlos, a la siguiente generación. Esto puede tener un impacto
      dramático en el rendimiento, ya que no gasta tiempo en redescubrir las soluciones parciales. Un problema con elLo
      elitismo es que puede causar que la GA converja a un óptimo local, así una raza puede quedarse cerca a un óptimo
      local.

## Alterer

Los alteradores son responsables de la diversidad genética del EvolutionStream.

Primero tenemos dos puntos de vista en la mutación:

- Explorar el espacio de búsqueda
    * Haciendo pequeños cambios, las mutaciones permiten que la población explore el espacio de búsqueda. Esta
      exploración a menudo es lenta comparada con el cruce, pero en problemas donde el cruce es disruptivo esto puede
      ser un camino importante para explorar el terreno.
- Mantener la diversidad
    * La mutación evita que una población converja a un mínimo local al detener la solución por acercarse demasiado
      entre sí. Un algoritmo genético puede mejorar la solución únicamente por el operador de mutación.

Lo que se quiere decir con lo anterior es que hay que buscar el equilibrio entre la mutación (exploración del espacio de
búsqueda) y la explotación (cruce entre individuos).

### Mutaciones

- Mutator
    * El mutador tiene que lidiar con el problema de que los genes están ordenados en una estructura jerárquica con tres
      niveles:
        - Selecciona un genotipo de la población con probabilidad `m`.
        - Selecciona un cromosoma, del genotipo seleccionado, con probabilidad `m`.
        - Selecciona un gene, del cromosoma seleccionado con probabilidad `m`.
- Gaussian mutator
    * El mutador gausiano realiza la mutación de un número de genes. Este mutador coge un nuevo valor basado en la
      distribución gausiana alrededor del valor actual del gen.
- Swap mutator
    * El mutador de intercambio cambia el orden de los genes en un cromosoma, con la esperanza de traer genes
      relacionados cercanos.

### Recombinaciones

- Single-point crossover
    * Cambia el cromosoma de dos hijos, tomando dos cromosomas y cortándolos aleatoriamente. Sin embargo, produce una
      mezcla muy lenta comparado con el cruce multi-punto o con el cruce uniforme.
- Multi-point crossover
    * Si ha sido creado con un punto es exactamente igual a single-point crossover, si no, pues simplemente intercambia
      varios puntos.
- Partially-matched crossover
    * Garantiza que todos los genes se encuentra exactamente una vez en cada cromosoma. No existen genes duplicados con
      esta estrategia.
- Uniform crossover
    * Los genes en el índice `i` son intercambiados con una probabilidad `ps`.
- Line crossover
    * Toma dos cromosomas números y los trata como un vector real.
- Intermediate crossover
    * Es bastante similar al line crossover. Difiere en la forma de como los parámetros internos aleatorios son
      generados y como se manejan los genes inválidos. 
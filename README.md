# Retrograde

> The past is a grotesque animal

> And in its eyes you see

> How completely wrong you can be

> -- Of Montreal

Retrograde is a small Clojure DSL that deals with the problem of accessing information and results from previous iterations during the **lazy** transformation of a sequence or during the **lazy** generation of an infinite sequence.

The main motivation for retrograde was the observation that in tricky cases, when a calculation requires access to previous iterations, Clojure programmers (the author at least!) tend to resort to using `loop`/`recur`, therefore losing laziness. Even if one sticks to `lazy-seq`, handling the passing of various bits of "state" to the next iteration is awkward at best, with the most horrific cases including use of real state such as atoms etc.

## Installation

Available from clojars, put this in your `project.clj` dependencies:

```
[retrograde "0.10"]
```

## Usage

### Access to previous results

Retrograde allows access to the results of previous iterations using the "prime" syntax. For example, here is an infinite list of Fibonacci numbers:

```clojure
(retrograde/calculate [x 1 (if (and x' x'') (+ x' x'') 0)])
```

`x` is defined as the name of the result in each iteration. The result of the previous iteration then becomes available as `x'` (read as "x-prime") and the result of the iteration before that is available as `x''`. You can go back as far as you like by adding more prime symbols (single quotes) to the name of your result.

The optional `1` value after the name of the result is the value of `x'` during the first iteration. This is followed by the code fragment that calculates the value of `x` in each iteration.

The code produced by the macro uses `lazy-seq` to make sure that the calculation happens in a lazy manner, without you having to handle the passing of "state" to the next iteration.

### Accumulators

#### Sum of all odd numbers so far

Retrograde also supports access to previous iterations via accumulators. Let's look into the (made up) problem of lazily transforming a sequence of numbers so that each output element is the input number added to all the odd numbers that have occurred in the sequence so far:

```clojure
> (retrograde/transform
   [odds [] (if (odd? x) (conj odds' x) odds')
    x (apply + (conj odds' x))]
   (range 10))
(0 1 3 4 8 9 15 16 24 25)
```

The first parameter of `transform` reads a bit like a let statement. First we define an accumulator called `odds`, which is initialised as an empty vector. We then define the code that calculates the value of odds in each iteration. As you can see, it simply looks at the value of `x` and decides whether to `conj` it to the value of `odds` from the previous iteration, `odds'`. The initial value for accumulators is **not** optional.

We then go on to name our result (`x`) and to provide the code for calculating it. We are adding `x` to `odds'` which contains all the odd numbers *so far* -- we don't want to use `odds` because that would contain `x` if it's odd. `odds` could be used if it made sense for the problem -- all the current values of the accumulators are available to the code calculating the result. The vector describing the calculations behaves like the `let` binding definitions in the sense that the calculations are evaluated in the order in which they appear, which means that each accumulator has access to the current value of all the accumulators that appear before it, and the result code block has access to the current values of all accumulators. While the accumulators are being calculated `x` hasn't been calculated yet, so `x` is still bound to the input value as it's read from the input sequence.

Finally, notice that in this example `x` is not initialised to any value, so in the first iteration the value of `x'` is `nil`. In a real-world situation the accumulator would probably hold the sum of the odd numbers so far, rather than a vector of the odd numbers.

#### Sliding windows

Let's have a look at how you would implement a sliding window of 5 elements (if somehow Rich Hickey decided to steal `partition` from you out of spite):

```clojure
> (pprint
   (retrograde/transform
    [o nil x
     x [o'''' o''' o'' o' o]]
    (range 10)))
([nil nil nil nil 0]
 [nil nil nil 0 1]
 [nil nil 0 1 2]
 [nil 0 1 2 3]
 [0 1 2 3 4]
 [1 2 3 4 5]
 [2 3 4 5 6]
 [3 4 5 6 7]
 [4 5 6 7 8]
 [5 6 7 8 9])
```

The following code would not work, because `x'` and the higher-order "primes" refer to previous *results* of the computation, **not** previous *elements* of the input sequence:

```clojure
(retrograde/transform [x [x'''' x''' x'' x' x]] (range 10))
```

If you actually run this, you get a very deeply nested tree of vectors within vectors within vectors etc, so that's why you need `o` to hold the value of `x` before it gets overwritten by the vector value on the last line of our transform.

Of course you don't have to refer to all the previous values of an accumulator. You can selectively look back at specific iterations. Here is a more unusual window:

```clojure
> (pprint
   (retrograde/transform
    [o nil x
     x [o'''' o'']]
    (range 10)))
([nil nil]
 [nil nil]
 [nil 0]
 [nil 1]
 [0 2]
 [1 3]
 [2 4]
 [3 5]
 [4 6]
 [5 7])
```

If your window is large, you can use an accumulator to implement a window of size 10:

```clojure
(retrograde/transform
 [window [] (conj (vec (take-last 9 window')) x)
  x window]
 (range 101))
```

## Syntax

The library provides two macros: `transform` and `calculate`. `transform` is for lazily transforming sequences while having access to results of previous iterations and `calculate` is for lazily generating infinite sequences in the same manner.

### transform

The syntax of `transform` is `(transform calculations collection)`, where `calculations` is:

```
[acc1 init1 code1
 acc2 init2 code2
 acc3 init3 code3
 ...
 result-name result-init? result-code]
```

`acc1` is the name of the first accumulator, `init1` is its initial value, `code1` is code to calculate the value of `acc1`. Similarly for `acc2`, `acc3` etc. You can have as many accumulators as you like, but in the expanded code they become parameters of a function, so you may hit an upper limit of how many parameters you can have in a function.

`result-name` is the name of the result, `result-init` is the original seed value of the result (available as `result-name'` in the first iteration), and `result-code` is the code that calculates the result of each iteration. `result-init` is optional.

### Scope and what is available where (**important!**)

As mentioned, in terms of scope and which values are available to what code, it helps to imagine the `calculations` block as a `let` bindings block. For example, while executing `code1`, `result-name` is bound to the original unchanged input element coming from the input sequence because it hasn't yet been overwritten by `result-code` in the last line of the block. In contrast, `acc2` is not available to `code1` because it appears further down and it hasn't been bound yet (but `acc2'` is available to `code1` because it was calculated in the previous iteration). `acc1` is available to `code2` because it appears before it, and the values of all accumulators from the current iteration are available to `result-code`.

### calculate

The syntax of `calculate` is `(calculate calculations)` where `calculations` has the same exact syntax as in `transform`. The difference is that there is no input sequence. The value of `result-name'` in the first iteration is whatever you initialise it to or `nil` if you don't.

### Primes

As we mentioned in the examples, values from previous iterations of all the accumulators and the result are available via the "prime" syntax. If the result is called `x`, then `x'` refers to its value in the previous iteration, `x''` to its value from 2 iterations ago etc. Same goes if you have an iterator called `acc`: `acc'` refers to its value in the previous iteration etc. Adding more prime symbols (single quotes) refers further back into previous iterations. Theoretically, you could go as far as you like with this syntax, but further lookbacks result in extra parameters in the function in the expanded code, so you may hit a Clojure limit concerning function parameters.

## A more practical example

Say we have a vector that looks like this:

```clojure
(def coll [5 2 [:a 55] 4 :a 5 [:b 777] 5 1 :a 2 3 :a :b :b])
```

Based on this vector, let's define a micro-format with some self-reference. The vector can contain:
 
 * numbers
 * named numbers which appear as vectors containing a name-number pair
 * references to named numbers which appear as keywords

We would like to resolve the references by replacing the keywords with the numbers that they refer to as encountered earlier in the sequence. We would also like to strip the named numbers and make them simple numbers. The resulting sequence should look like that:

```clojure
(5 2 55 4 55 5 777 5 1 55 2 3 55 777 777)
```

This can be achieved using the following code:

```clojure
(retrograde/transform
 [lookup {} (if (vector? x)
              (let [[k v] x] (assoc lookup' k v))
              lookup')
  x (cond (vector? x) (second x)
          (keyword? x) (lookup x)
          :else x)]
 coll)
```

The `lookup` accumulator is initialised as an empty map, and each input is checked to see if it's a vector, in which case it's `assoc`ed to the previous value of lookup. Don't forget to return `lookup'` unchanged if the element wasn't a reference, otherwise it won't be carried to the next iteration.

`x` then either strips the name of the number, looks-up keywords in `lookup` or returns the value unchanged.

## Credits

Special thanks to Rupert Ede for insightful discussions and feedback.

## License

Copyright © 2014 Efstathios Sideris

Distributed under the Eclipse Public License, the same as Clojure.

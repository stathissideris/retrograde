(ns retrograde)

(defn map-with-acc
  "fun is a function with arity equal to the number of accumulators
  +1. It's expected to return a vector of the same length as its
  arity.

  accs is a vector of initial states for all the accumulators.

  coll is the collection to be processed."
  [fun accs coll]
  (if (empty? coll)
    []
    (let [res (apply fun (first coll) accs)]
      (lazy-seq
       (cons
        (first res)
        (map-with-acc fun (rest res) (rest coll)))))))

(defn- prime? [s]
  (and (symbol? s) (.endsWith (name s) "'")))

(defn- dec-prime [s]
  (if-not (= \' (last (name s)))
    s
    (symbol (apply str (butlast (name s))))))

(defn- prime-count [s]
  (count (re-find #"\'+$" (name s))))

(defn- main-symbol [s]
  (symbol (re-find #"[^\']+" (name s))))

(defn- all-primes [s]
  (take (prime-count s) (iterate dec-prime s)))

(defn- derive-primes [primes]
  (->> primes
       (group-by main-symbol)
       (vals)
       (map #(last (sort-by prime-count %)))
       (mapcat all-primes)))

(defmacro transform
  "Macro for lazily transforming a sequence while having access to
  results of previous iterations.

  The syntax is (transform calculations collection) where collection
  is the sequence to transform and calculations is:

  [acc1 init1 code1
   acc2 init2 code2
   acc3 init3 code3
   ...
   result-name result-init? result-code]

  acc1 is the name of the first accumulator, init1 is its initial
  value, code1 is code to calculate the value of acc1. Similarly for
  acc2, acc3 etc. You can have as many accumulators as you like, but
  in the expanded code they become parameters of a function, so you
  may hit an upper limit of how many parameters you can have in a
  function.

  result-name is the name of the result, result-init is the initial
  seed value of the result (available as result-name' in the first
  iteration), and result-code is the code that calculates the result
  of each iteration. result-init is optional.

  Previous values of iterators and results can accessed using their
  names with a number of \"prime\" characters (single quotes) attached
  at the end of the symbol. For example, result-name' is the value of
  result-name in the previous iteration, acc1'' is the value of acc1
  two iterations ago etc."
  
  [calcs coll]
  (let [prime (fn [s] (symbol (str (name s) "'")))
        calcs (partition-all 3 calcs)

        element-def (last calcs)
        element-name (first element-def)
        element-init (if (= (count element-def) 2) nil (second element-def))
        element-code (last element-def)

        ;;make calcs consistent
        calcs (conj (vec (butlast calcs)) [element-name element-init element-code])
        inits (zipmap (map first calcs) (map second calcs))

        acc-defs (butlast calcs)
        acc-names (map first acc-defs)
        acc-codes (map last acc-defs)

        mentioned-primes (->> calcs
                              (flatten)
                              (filter prime?)
                              (derive-primes))
        default-primes (map prime (map first calcs))        
        primes (sort (distinct (concat mentioned-primes default-primes)))
        
        function-params (vec (concat [element-name] primes))
        function-return (vec (map dec-prime function-params))]
    `(map-with-acc
      (fn ~'map-with-acc-fun ~function-params
        (let [~@(mapcat vector acc-names acc-codes)
              ~element-name ~element-code]
          ~function-return))
      ;;init values
      ~(vec (map inits (rest function-return)))
      ~coll)))

(defmacro calculate
  "Macro for lazily calculating an infinite sequence while having
  access to results of previous iterations.

  The syntax is (calculate calculations) where calculations is:

  [acc1 init1 code1
   acc2 init2 code2
   acc3 init3 code3
   ...
   result-name result-init? result-code]

  acc1 is the name of the first accumulator, init1 is its initial
  value, code1 is code to calculate the value of acc1. Similarly for
  acc2, acc3 etc. You can have as many accumulators as you like, but
  in the expanded code they become parameters of a function, so you
  may hit an upper limit of how many parameters you can have in a
  function.

  result-name is the name of the result, result-init is the initial
  seed value of the result (available as result-name' in the first
  iteration), and result-code is the code that calculates the result
  of each iteration. result-init is optional.

  Previous values of iterators and results can accessed using their
  names with a number of \"prime\" characters (single quotes) attached
  at the end of the symbol. For example, result-name' is the value of
  result-name in the previous iteration, acc1'' is the value of acc1
  two iterations ago etc."
  [calcs]
  `(transform ~calcs (repeat nil)))

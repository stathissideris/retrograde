(ns retrograde-test
  (:require [retrograde :refer :all]
            [clojure.test :refer :all]))

(deftest fibonacci
  (is (= '(0 1 1 2 3 5 8 13 21 34)
         (take 10 (calculate [x 1 (if (and x' x'') (+ x' x'') 0)])))))

(deftest all-odds-so-far
  (is (= '(0 1 3 4 8 9 15 16 24 25)
         (transform
          [odds [] (if (odd? x) (conj odds' x) odds')
           x (apply + (conj odds' x))]
          (range 10)))))

(deftest window
  (is (= '([nil nil nil nil 0]
           [nil nil nil 0 1]
           [nil nil 0 1 2]
           [nil 0 1 2 3]
           [0 1 2 3 4]
           [1 2 3 4 5]
           [2 3 4 5 6]
           [3 4 5 6 7]
           [4 5 6 7 8]
           [5 6 7 8 9])
         (transform
          [o nil x
           x [o'''' o''' o'' o' o]]
          (range 10))))
  (is (= '([nil nil]
           [nil nil]
           [nil 0]
           [nil 1]
           [0 2]
           [1 3]
           [2 4]
           [3 5]
           [4 6]
           [5 7])
         (transform
          [o nil x
           x [o'''' o'']]
          (range 10)))))

(deftest flatten-list-with-references
  (is (= '(5 2 55 4 55 5 777 5 1 55 2 3 55 777 777)
         (transform
          [lookup {} (if (vector? x)
                       (let [[k v] x] (assoc lookup' k v))
                       lookup')
           x (cond (vector? x) (second x)
                   (keyword? x) (lookup x)
                   :else x)]
          [5 2 [:a 55] 4 :a 5 [:b 777] 5 1 :a 2 3 :a :b :b]))))

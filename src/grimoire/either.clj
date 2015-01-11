(ns grimoire.either
  "Quick and dirty implementation of something like Haskell's Either[Success,
  Failure] for Clojure. Not the nicest thing in the world, but it'll do the
  job. Used to indicate success and failure throughout the lib-grimoire API.")

(defn succeed
  "λ [t] → Succeed[t]

  Type constructor. Returns a pair [:succeed x] for all x.

  ∀x (succeed? (succeed x)) == true
  ∀x (result (succeed x)) == x"
  [x]
  [:succeed x])

(defn succeed?
  "λ [t] → Bool

  Type predicate. Matches only two-vectors [:succeed x] as documented in the
  succeed constructor."
  [x]
  {:pre [(vector? x)
         (= 2 (count x))]}
  (= :succeed (first x)))

(defn result
  "λ [Succeed[t]] → t

  Value extractor. For a succeed value, unboxes the result of the
  succeed. Otherwise encounters an assertion failure (type error)."
  [x]
  {:pre [(succeed? x)]}
  (second x))

(defn fail
  "λ [t] → Fail[x]

  Type constructor. Returns a pair [:fail x] for all x.

  ∀x (fail? (fail x)) == true
  ∀x (message (fail x)) == x"
  [x]
  [:fail x])

(defn fail? [x]
  "λ [t] → Bool

  Type predicate. Matches only two-vectors [:fail x] as documented in
  the fail constructor."
  {:pre [(vector? x)
         (= 2 (count vector))]}
  (= :fail (first x)))

(defn message
  "λ [Fail[t]] → t

  Value extractor. For a failure value, unboxes the result of the
  failure. Otherwise encounters an assertion failure (type error)."
  [x]
  {:pre [(fail? x)]}
  (second x))

(defn either?
  "λ [t] → Bool

  Type predicate, matching either succeed or failure structures. Intended as a
  postcondition for maybe functions."
  [x]
  (and (vector? x)
       (= 2 (count x))
       (#{:succeed :fail} (first x))))

(defmacro with-result
  "This macro is a helper designed to emulate the Haskell pattern matching which
  Clojure lacks by default.

  Usage:
  (with-result [x (could-fail-form)]
    (use-x x))

  In the two-arity case, the value expression is evaluated and if a failure is
  generated it is passed back up the stack implicitly as this is assumed to be
  the common case. If the possibly failing expression succeeds, then the result
  is extracted and let-bound to the binding form. The binding form may contain
  destructuring. Exceptions occuring inside the left form will be caught and
  bound into Failure values.

  Usage:
  (with-result [x (could-fail-form)]
    (use-x x)         ; x is the Result
    (failure-case x)) ; x is the Message

  In the three-arity case, the expression value is evaluated, and unaltered
  value of the possibly failing form is bound to the given symbol. If the result
  value is a success structure, then the \"left\" form is evaluated, otherwise
  the \"right\" form is evaluated. No implicit result or error destructuring is
  provided in this case. Exceptions occuring inside either form will be caught
  and bound into Failure values."

  ([[binding form] left]
   `(let [res# ~form]
      (if (succeed? res#)
        (let [~binding res#]
          (try ~left
               (catch Exception e# (fail e#))))
        res#)))

  ([[binding form] left right]
   {:pre [(symbol? binding)]}
   `(let [~binding ~form]
      (try (if (succeed? ~binding)
             ~left ~right)
           (catch Exception e# (fail e#))))))

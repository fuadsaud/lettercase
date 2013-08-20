(ns org.tobereplaced.lettercase
  "Declarative case conversion."
  (:require [clojure.string
             :refer [join split capitalize lower-case upper-case]]
            [org.tobereplaced.lettercase.internal :refer [docstring]])
  (:import [clojure.lang Keyword Symbol]))

(defn separator-pattern
  "Returns a composite pattern that can be used to break a string into
  words.  The resulting pattern will match any space character, an
  upper letter immediately followed by an upper letter immediately
  followed by a lower letter, a lower letter immediately followed by
  an upper letter, any letter immediately followed by a number, and
  any number immediately followed by a number."
  [& {:keys [space upper letter number]
      :or {space #"[\- _,/|]"
           upper #"\p{Lu}"
           letter #"\p{L}"
           number #"\p{N}"}}]
  (let [lower (format "[%s&&[^%s]]" letter upper)
        pair (fn [before after] (format "(?<=%s)(?=%s)" before after))]
    (->> [space
          (pair upper (str upper lower))
          (pair lower upper)
          (pair letter number)
          (pair number letter)]
         (join \|)
         re-pattern)))

(def ^:private case-functions
  "A map from case words to a pair with a function for its first word
  and function for the rest of its words."
  {:capitalized [capitalize capitalize]
   :sentence [capitalize lower-case]
   :mixed [lower-case capitalize]
   :upper [upper-case upper-case]
   :lower [lower-case lower-case]})

(def ^:private space-strings
  "A map from spacing words to their corresponding strings."
  {:space " "
   :underscore "_"
   :hyphen "-"
   nil ""})

(doseq [[casing [first-fn rest-fn]] case-functions
        [spacing space-string] space-strings]
  (let [default-separator-pattern (separator-pattern)
        fn-impl (fn impl
                  ([s]
                     (impl s default-separator-pattern))
                  ([s re]
                     (let [[word & more] (split s re)]
                       (join space-string
                             (cons (first-fn word) (map rest-fn more))))))
        fn-symbol (->> [casing spacing]
                       (remove nil?)
                       (map name)
                       (join "-")
                       symbol)]
    (intern *ns*
            (with-meta fn-symbol
              {:doc (docstring fn-symbol fn-impl casing space-string)
               :arglists '([s] [s re])})
            fn-impl)))

;;; TODO: Create a variadic function for this.
(defprotocol AlterName
  (alter-name [this f] "Alters the name of this with f."))

(extend-protocol AlterName
  Keyword (alter-name [this f] (->> this name f (keyword (namespace this))))
  Symbol (alter-name [this f] (->> this name f (symbol (namespace this)))))

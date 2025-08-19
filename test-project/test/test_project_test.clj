(ns test-project-test
  (:require [clojure.test :refer [deftest is]]))

(deftest a-test
  (is (= 1 1))
  (is (= 1 0)))

(deftest b-test
  (is (= 1 1)))

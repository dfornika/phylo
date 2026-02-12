(ns app.state-test
  "Tests for state import/export validation in [[app.state]]."
  (:require [cljs.test :refer [deftest testing is]]
            [app.state :as state]))

(deftest apply-export-state-coerces-palette
  (testing "Invalid palette values default to :bright"
    ;; Reset atoms to known state
    (reset! state/!color-by-palette :contrast)
    
    ;; Apply export with nil palette
    (state/apply-export-state! {:color-by-palette nil})
    (is (= :bright @state/!color-by-palette))
    
    ;; Apply export with invalid string
    (state/apply-export-state! {:color-by-palette "invalid"})
    (is (= :bright @state/!color-by-palette))
    
    ;; Apply export with invalid keyword
    (state/apply-export-state! {:color-by-palette :unknown})
    (is (= :bright @state/!color-by-palette))
    
    ;; Apply export with missing palette key (should use default from export-defaults)
    (state/apply-export-state! {:newick-str nil})
    (is (= :bright @state/!color-by-palette)))
  
  (testing "Valid categorical palette values are preserved"
    (state/apply-export-state! {:color-by-palette :bright})
    (is (= :bright @state/!color-by-palette))
    
    (state/apply-export-state! {:color-by-palette :contrast})
    (is (= :contrast @state/!color-by-palette))
    
    (state/apply-export-state! {:color-by-palette :pastel})
    (is (= :pastel @state/!color-by-palette)))
  
  (testing "Valid gradient palette values are preserved"
    (state/apply-export-state! {:color-by-palette :blue-red})
    (is (= :blue-red @state/!color-by-palette))
    
    (state/apply-export-state! {:color-by-palette :teal-gold})
    (is (= :teal-gold @state/!color-by-palette))))

(deftest apply-export-state-coerces-type-override
  (testing "Invalid type override values default to :auto"
    ;; Reset atoms to known state
    (reset! state/!color-by-type-override :categorical)
    
    ;; Apply export with nil type override
    (state/apply-export-state! {:color-by-type-override nil})
    (is (= :auto @state/!color-by-type-override))
    
    ;; Apply export with invalid string
    (state/apply-export-state! {:color-by-type-override "invalid"})
    (is (= :auto @state/!color-by-type-override))
    
    ;; Apply export with invalid keyword
    (state/apply-export-state! {:color-by-type-override :unknown})
    (is (= :auto @state/!color-by-type-override))
    
    ;; Apply export with missing type override key (should use default from export-defaults)
    (state/apply-export-state! {:newick-str nil})
    (is (= :auto @state/!color-by-type-override)))
  
  (testing "Valid type override values are preserved"
    (state/apply-export-state! {:color-by-type-override :auto})
    (is (= :auto @state/!color-by-type-override))
    
    (state/apply-export-state! {:color-by-type-override :categorical})
    (is (= :categorical @state/!color-by-type-override))
    
    (state/apply-export-state! {:color-by-type-override :numeric})
    (is (= :numeric @state/!color-by-type-override))
    
    (state/apply-export-state! {:color-by-type-override :date})
    (is (= :date @state/!color-by-type-override))))

(deftest apply-export-state-handles-versioned-payload
  (testing "Accepts versioned payload wrapper and coerces invalid values"
    ;; Reset atoms to known state
    (reset! state/!color-by-palette :contrast)
    (reset! state/!color-by-type-override :categorical)
    
    ;; Apply versioned export with invalid values
    (state/apply-export-state! 
     {:version 1 
      :state {:color-by-palette :invalid
              :color-by-type-override :invalid}})
    
    (is (= :bright @state/!color-by-palette))
    (is (= :auto @state/!color-by-type-override))))

(deftest apply-export-state-handles-hand-edited-export
  (testing "Safely handles malformed hand-edited exports"
    ;; Reset atoms to known state
    (reset! state/!color-by-palette :contrast)
    (reset! state/!color-by-type-override :categorical)
    
    ;; Simulate a hand-edited export with unexpected values
    (state/apply-export-state! 
     {:newick-str "((A:1,B:1):1,C:2);"
      :color-by-palette "bright"  ;; Wrong type (string instead of keyword)
      :color-by-type-override 123})  ;; Wrong type (number)
    
    ;; Should default to safe values
    (is (= :bright @state/!color-by-palette))
    (is (= :auto @state/!color-by-type-override))))

(ns app.dev-preload
  "Dev-only preload: wires up expound for readable spec errors and
  instruments all fdef'd functions so :args specs are checked at
  call time during development.

  Loaded via shadow-cljs :devtools {:preloads [... app.dev-preload]}.
  Stripped entirely from release builds."
  (:require [cljs.spec.alpha :as s]
            [cljs.spec.test.alpha :as stest]
            [expound.alpha :as expound]
            ;; Require all namespaces that have fdefs so stest/instrument
            ;; can find them. The specs themselves are registered in
            ;; app.specs, but the vars must be loaded for instrument to
            ;; attach checkers.
            [app.newick]
            [app.csv]
            [app.date]
            [app.tree]
            [app.scale]
            [app.layout]
            [app.color]
            [app.import.nextstrain]
            [app.import.arborview]
            [app.util]
            [app.specs]
            ;; Register custom generators for recursive/domain specs.
            ;; Must be loaded after app.specs so it can re-def with s/with-gen.
            [app.spec-generators]))

;; Use expound for human-readable spec error messages
(set! s/*explain-out* expound/printer)

;; Instrument all fdef'd functions — checks :args specs on every call
(stest/instrument)

(js/console.info "[dev-preload] expound printer active, specs instrumented")

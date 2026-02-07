(ns app.core
  "Application entry point for Phylo, a phylogenetic tree viewer.

  This namespace is the thin shell that wires together the component
  tree and mounts it into the DOM. All layout algorithms live in
  [[app.tree]], layout constants in [[app.layout]], and UI components
  in the `app.components.*` namespaces."
  (:require [uix.core :refer [defui $]]
            [uix.dom]
            [app.state :as state]
            [app.components.viewer :refer [TreeContainer]]))

;; ===== App Shell =====

(defui app
  "Root application component.

  Wraps the component tree with [[state/AppStateProvider]] so all
  descendants can access shared state via context."
  []
  ($ state/AppStateProvider
     ($ TreeContainer {:width-px 1200
                       :component-height-px 800})))

(defonce root
  (when (exists? js/document)
    (when-let [el (js/document.getElementById "app")]
      (uix.dom/create-root el))))

(defn render
  "Renders the root [[app]] component into the DOM."
  []
  (when root
    (uix.dom/render-root ($ app) root)))

(defn ^:export init
  "Exported entry point called by shadow-cljs on page load."
  []
  (render))

(defn ^:dev/after-load re-render
  "Hot-reload hook called by shadow-cljs after code changes.
  Re-renders from root so that new component definitions take effect.
  State is preserved because it lives in `defonce` atoms in [[app.state]]."
  []
  (render))

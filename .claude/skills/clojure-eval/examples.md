# clj-nrepl-eval Examples

## Discovery

```bash
clj-nrepl-eval --connected-ports
```

## shadow-cljs: Switch to CLJS Mode

shadow-cljs nREPL servers start in JVM Clojure mode. You must switch to a CLJS
build REPL before you can require `.cljs` namespaces:

```bash
# Switch to CLJS mode for the :app build
clj-nrepl-eval -p 41371 "(shadow/repl :app)"

# Now ClojureScript namespaces are available
clj-nrepl-eval -p 41371 "(require '[app.newick :as newick] :reload)"
clj-nrepl-eval -p 41371 "(newick/newick->map \"(A:0.1,B:0.2)Root:0.3;\")"

# Exit CLJS mode back to JVM Clojure
clj-nrepl-eval -p 41371 ":cljs/quit"
```

**Signs you forgot this step:**
- `FileNotFoundException: Could not locate app/newick__init.class ...` — the JVM
  can't find `.cljs` files because it's still in Clojure mode
- Output contains `;; shadow-cljs repl is NOT in CLJS mode`

The session is persistent, so you only need to switch once. Subsequent
`clj-nrepl-eval` calls on the same port stay in CLJS mode.

## Heredoc for Multiline Code

```bash
clj-nrepl-eval -p 7888 <<'EOF'
(defn greet [name]
  (str "Hello, " name "!"))

(greet "Claude")
EOF
```

### Heredoc Simplifies String Escaping

Heredoc avoids shell escaping issues with quotes, backslashes, and special characters:

```bash
# With heredoc - no escaping needed
clj-nrepl-eval -p 7888 <<'EOF'
(def regex #"\\d{3}-\\d{4}")
(def message "She said \"Hello!\" and waved")
(def path "C:\\Users\\name\\file.txt")
(println message)
EOF

# Without heredoc - requires complex escaping
clj-nrepl-eval -p 7888 "(def message \"She said \\\"Hello!\\\" and waved\")"
```

## Working with Project Namespaces

```bash
# Test a function after requiring
clj-nrepl-eval -p 7888 <<'EOF'
(require '[clojure-mcp-light.delimiter-repair :as dr] :reload)
(dr/delimiter-error? "(defn foo [x]")
EOF
```

## Verify Compilation After Edit

```bash
# If this returns nil, the file compiled successfully
clj-nrepl-eval -p 7888 "(require 'clojure-mcp-light.hook :reload)"
```

## Session Management

```bash
# Reset session if state becomes corrupted
clj-nrepl-eval -p 7888 --reset-session
```

## Common Workflow Patterns

### Load, Test, Iterate

```bash
# After editing a file, reload and test in one command
clj-nrepl-eval -p 7888 <<'EOF'
(require '[my.namespace :as ns] :reload)
(ns/my-function test-data)
EOF
```

### Run Tests After Changes

```bash
clj-nrepl-eval -p 7888 <<'EOF'
(require '[my.project.core :as core] :reload)
(require '[my.project.core-test :as test] :reload)
(clojure.test/run-tests 'my.project.core-test)
EOF
```

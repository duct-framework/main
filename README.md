# Duct Main

A tool for running applications built with the [Duct][] framework.

Note that this tool is currently experimental, and most available Duct
components will not work with this yet.

[duct]: https://github.com/duct-framework/duct

## Installation

Add the following dependency and alias to your `deps.edn` file:

```edn
{:deps {org.duct-framework/main {:mvn/version "0.3.1"}}
 :aliases {:duct {:main-opts ["-m" "duct.main"]}}}
```

Then run:

```sh
clojure -M:duct --init
```

This will create a minimal `duct.edn` file.

## Configuration

Duct Main uses a configuration file, `duct.edn`, located at the root of
your repository. Like `deps.edn`, it is a map, and it has two valid
keys:

- `:system` - an [Integrant][] configuration map
- `:vars`   - a map of variables to bind to the configuration map

For example:

```edn
{:vars
 {port {:type :int, :env PORT, :arg port, :default 3000}
  name {:type :str, :arg name, :default "World"}}

 :system
 {:example.handler/hello
  {:name #ig/var name}

  :duct.server.http/jetty
  {:port    #ig/var port
   :handler #ig/ref :example.handler/hello}}}
```

[integrant]: https://github.com/weavejester/integrant

### Vars

Vars are how we get information into the system from external sources.
Currently there are two available sources: environment variables and
command-line flags.

Vars are symbols, and their value is defined by a map with the following
keys:

- `:arg`     - the command-line flag to get the var from
- `:default` - the default value of the var
- `:env`     - the environment variable to get the var from
- `:type`    - the type of the var, one of: `:str`, `:int`

Vars can also be defined through Integrant's [annotations][]. When Duct
starts, it looks through the keys in the configuration for annotations
with the key `:duct/vars`.

[annotations]: https://github.com/weavejester/integrant#annotations

## Command line

You can run your Duct application with:

```sh
clojure -M:duct --main
```

And you can start a development REPL with:

```sh
clojure -M:duct --repl
```

For more information on the Duct tool, run:

```sh
clojure -M:duct --help
```

It's also recommended that you create a shell alias to make these
commands briefer:

```sh
alias duct="clojure -M:duct"
```

## License

Copyright © 2025 James Reeves

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

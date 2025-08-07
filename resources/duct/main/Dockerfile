FROM clojure:tools-deps

# Cache deps
COPY deps.edn /usr/src/app/deps.edn
WORKDIR /usr/src/app
RUN ["clojure", "-M", "-e", ""]

# Run compilers
COPY . /usr/src/app
RUN ["clojure", "-M:duct", "-mvk", ":duct/compiler"]

# Set default port
ENV PORT=3000
EXPOSE 3000/tcp

CMD ["clojure", "-M:duct", "-mvk", ":duct/daemon"]

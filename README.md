# Go-wasm-newsapi

This is a GO app using Webassembly and consuming the REST API NewsApi.org


Hosting on firebase
------
https://go-wasm-news-api.firebaseapp.com

Screens
------------
<img src="https://github.com/theristes/go-wasm-newsapi/blob/master/20190212_170235.gif">


Quickstart
------
  go get -u github.com/theristes/go-wasm-newsapi
  cd ${GOPATH}/src/github.com/theristes/go-wasm-newsapi

So open it with your favorite code editor

Run
------
Open the terminal and run: 

  go run server.go
  

Update the wasm
------
In order to update the file main.wasm because of a possible changes in main.go, run it:

GOARCH=wasm GOOS=js go build -o main.wasm main.go build -o main.wasm main.go     


Datasource
------
newsapi.org - (News API - A JSON API for live news and blog articles)
(https://www.newsapi.org)


libs
------
(Bootstrap) - https://getbootstrap.com/ 

(dennwc/dom) -  https://github.com/dennwc/dom


How to Contribute
------
just make a pull request...


License
------
Distributed under MIT License, please see license file within the code for more details.

## Authors

* **Theristes Gomes**




package main

import (
	"encoding/json"
	"io/ioutil"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"syscall/js"
	"time"

	"github.com/dennwc/dom"
)

var wg sync.WaitGroup // 1

type News struct {
	Status       string `json:"status"`
	TotalResults int    `json:"totalResults"`
	Articles     []struct {
		Source struct {
			ID   interface{} `json:"id"`
			Name string      `json:"name"`
		} `json:"source"`
		Author      string    `json:"author"`
		Title       string    `json:"title"`
		Description string    `json:"description"`
		URL         string    `json:"url"`
		URLToImage  string    `json:"urlToImage"`
		PublishedAt time.Time `json:"publishedAt"`
		Content     string    `json:"content"`
	} `json:"articles"`
}

func search(i []js.Value) {
	go buildPage()
}

func main() {
	buildPage()
	c1 := make(chan struct{}, 0)
	js.Global().Set("search", js.NewCallback(search))
	<-c1
}

func buildPage() {
	query := js.Global().Get("document").Call("getElementById", "basic-search").Get("value").String()
	if len(query) > 3 {
		println(query)

		divSearchData := dom.GetDocument().GetElementById("search-data")
		divSearchData.SetInnerHTML("")

		divHeader := dom.Doc.CreateElement("div")
		divHeader.SetClassName("col-4")
		divListGroup := dom.Doc.CreateElement("div")
		divListGroup.SetClassName("list-group")
		divListGroup.SetAttribute("role", "tablist")

		divTabHeader := dom.Doc.CreateElement("div")
		divTabHeader.SetClassName("col-8")
		divTabListGroup := dom.Doc.CreateElement("div")
		divTabListGroup.SetClassName("tab-content")
		divTabListGroup.SetId("nav-tabContent")
		divTabListGroup.SetAttribute("role", "tablist")

		for index, item := range getData(query).Articles {
			strtIndex := strconv.Itoa(index)
			a := dom.Doc.CreateElement("a")
			if index == 0 {
				a.SetClassName("list-group-item list-group-item-action active")
			} else {
				a.SetClassName("list-group-item list-group-item-action")
			}

			a.SetId("list-" + strtIndex + "-list")
			a.SetAttribute("data-toggle", "list")

			a.SetAttribute("href", "#list-"+strtIndex)
			a.SetAttribute("role", "tab")
			a.SetAttribute("aria-controls", "home")
			a.SetInnerHTML(item.Title)
			divListGroup.AppendChild(a)

			div := dom.Doc.CreateElement("div")
			if index == 0 {
				div.SetClassName("tab-pane fade show active")
			} else {
				div.SetClassName("tab-pane fade")
			}
			div.SetId("list-" + strtIndex)
			div.SetAttribute("role", "tabpanel")
			div.SetAttribute("aria-labelledby", "list-home-list")

			divCardMb3 := dom.Doc.CreateElement("div")
			divCardMb3.SetClassName("card mb-3")

			imgCardTop := dom.Doc.CreateElement("img")
			imgCardTop.SetAttribute("src", item.URLToImage)

			divCardBody := dom.Doc.CreateElement("div")
			divCardBody.SetClassName("card-body")

			h5cardTitle := dom.Doc.CreateElement("h5")
			h5cardTitle.SetClassName("card-title")
			h5cardTitle.SetInnerHTML(item.Title)

			pCardText := dom.Doc.CreateElement("p")
			pCardText.SetClassName("card-text")
			pCardText.SetInnerHTML(item.Content)

			pCardTextForSmall := dom.Doc.CreateElement("p")
			pCardTextForSmall.SetClassName("card-text")

			smallTextMuthed := dom.Doc.CreateElement("small")
			smallTextMuthed.SetClassName("text-muted")
			smallTextMuthed.SetInnerHTML(item.PublishedAt.Format("01-02-2006") + " - " + item.Author)

			aPrimary := dom.Doc.CreateElement("a")
			aPrimary.SetClassName("btn btn-primary")
			aPrimary.SetAttribute("href", item.URL)
			aPrimary.SetInnerHTML("Read More in " + item.Source.Name)

			divCardMb3.AppendChild(imgCardTop)
			div.AppendChild(divCardMb3)

			divCardBody.AppendChild(h5cardTitle)
			divCardBody.AppendChild(pCardText)
			divCardBody.AppendChild(pCardTextForSmall)
			divCardBody.AppendChild(aPrimary)
			divCardMb3.AppendChild(divCardBody)

			divTabListGroup.AppendChild(div)

		}

		divHeader.AppendChild(divListGroup)
		divSearchData.AppendChild(divHeader)

		divTabHeader.AppendChild(divTabListGroup)
		divSearchData.AppendChild(divTabHeader)
	}

}

func getData(query string) News {
	key := "f12ac86c7e2f44679ca6a11acf894116"
	query = strings.Trim(query, "")
	dt := time.Now()

	url := "https://newsapi.org/v2/everything?q="
	url = url + query
	url = url + "&from=" + dt.Format("01-02-2006") + "&sortBy=publishedAt&apiKey=" + key

	println(url)

	req, _ := http.NewRequest("GET", url, nil)
	req.Header.Add("cache-control", "no-cache")
	res, _ := http.DefaultClient.Do(req)

	defer res.Body.Close()
	body, _ := ioutil.ReadAll(res.Body)

	var news News
	json.Unmarshal(body, &news)

	return news
}

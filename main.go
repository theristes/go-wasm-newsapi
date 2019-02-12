package main

import (
	"encoding/json"
	"fmt"
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
	go designingData(js.Global().Get("document").Call("getElementById", "basic-search").Get("value").String())
}

func main() {
	designingData("news")
	c1 := make(chan struct{}, 0)
	js.Global().Set("search", js.NewCallback(search))
	<-c1
}

func formatDurationTime(publishedAt time.Time) string {
	return fmt.Sprintf("%d", int(time.Now().Sub(publishedAt).Minutes())) + "m ago"
}

func designingData(query string) {
	if len(query) <= 3 {
		return
	}
	divSearchData := dom.GetDocument().GetElementById("search-data").SetInnerHTML("")
	for i, item := range getData(query).Articles {
		strI := strconv.Itoa(i)
		divSearchData.
			AddChild(dom.Doc.CreateElement("div").SetClassName("list-group").
				AddChild(dom.Doc.CreateElement("a").SetClassName("list-group-item list-group-item-action").
					SetAttribute("data-toggle", "modal").SetAttribute("data-target", "#modal"+strI).
					AddChild(dom.Doc.CreateElement("div").SetClassName("d-flex w-100 justify-content-between").
						AddChild(dom.Doc.CreateElement("h5").SetClassName("mb-1 mr-4 text-truncate").SetInnerHTML(item.Title)).
						AddChild(dom.Doc.CreateElement("small").SetInnerHTML(formatDurationTime(item.PublishedAt)))).
					AddChild(dom.Doc.CreateElement("p").SetClassName("mb-1").SetInnerHTML(item.Description)).
					AddChild(dom.Doc.CreateElement("small").SetClassName("text-muted").SetInnerHTML(item.Source.Name)))).
			AddChild(dom.Doc.CreateElement("div").SetClassName("modal fade").SetId("modal"+strI).SetAttribute("tabindex", "-1").SetAttribute("role", "dialog").
				SetAttribute("aria-labelledby", "#modal"+strI).SetAttribute("aria-hidden", "true").
				AddChild(dom.Doc.CreateElement("div").SetClassName("modal-dialog").SetAttribute("role", "document").
					AddChild(dom.Doc.CreateElement("div").SetClassName("modal-content").
						AddChild(dom.Doc.CreateElement("div").SetClassName("modal-header").
							AddChild(dom.Doc.CreateElement("h5").SetClassName("modal-title text-truncate").SetInnerHTML(item.Source.Name)).
							AddChild(dom.Doc.CreateElement("button").SetClassName("close").SetAttribute("type", "button").SetAttribute("data-dismiss", "modal").SetAttribute("aria-label", "Close").
								AddChild(dom.Doc.CreateElement("span").SetAttribute("aria-hidden", "true").SetInnerHTML("&times;")))).
						AddChild(dom.Doc.CreateElement("div").SetClassName("modal-body card").
							AddChild(dom.Doc.CreateElement("img").SetClassName("card-img-top").SetAttribute("src", item.URLToImage)).
							AddChild(dom.Doc.CreateElement("div").SetClassName("card-body").
								AddChild(dom.Doc.CreateElement("h5").SetClassName("card-title").SetInnerHTML(item.Title)).
								AddChild(dom.Doc.CreateElement("p").SetClassName("card-text").SetInnerHTML(item.Description)).
								AddChild(dom.Doc.CreateElement("footer").SetClassName("footer text-muted float-right").SetInnerHTML(item.PublishedAt.Format("01-02-2006")))).
							AddChild(dom.Doc.CreateElement("ul").SetClassName("list-group list-group-flush").
								AddChild(dom.Doc.CreateElement("li").SetClassName("list-group-item").SetInnerHTML("<b>Author: </b>" + item.Author)).
								AddChild(dom.Doc.CreateElement("li").SetClassName("list-group-item").SetInnerHTML("<b>Source: </b>" + item.Source.Name))).
							AddChild(dom.Doc.CreateElement("div").SetClassName("card-body").
								AddChild(dom.Doc.CreateElement("a").SetClassName("btn btn-primary btn-sm active float-right").
									SetAttribute("href", item.URL).SetAttribute("role", "button").SetAttribute("aria-pressed", "true").SetInnerHTML("Check the full article")))))))
	}

}

func getData(query string) News {
	key := "<<API KEY>>"
	query = strings.Trim(query, "")
	dt := time.Now()

	url := "https://newsapi.org/v2/everything?q="
	url = url + query
	url = url + "&from=" + dt.Format("01-02-2006") + "&sortBy=publishedAt&apiKey=" + key

	println(url)

	req, _ := http.NewRequest("GET", url, nil)
	res, _ := http.DefaultClient.Do(req)

	defer res.Body.Close()
	body, _ := ioutil.ReadAll(res.Body)

	var news News
	json.Unmarshal(body, &news)

	return news
}

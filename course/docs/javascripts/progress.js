/* Progress lives in the browser (localStorage), like a self-paced course should. */
(function () {
  var KEY = "quest.progress.v1";
  var Q = window.QUEST || { lessons: [], courses: [] };

  function load() {
    try { return JSON.parse(localStorage.getItem(KEY)) || { done: {}, quiz: {} }; }
    catch (e) { return { done: {}, quiz: {} }; }
  }
  function save(state) { localStorage.setItem(KEY, JSON.stringify(state)); }

  function siteRoot() {
    var logo = document.querySelector(".md-header__button.md-logo");
    var href = logo ? logo.getAttribute("href") : "/";
    return new URL(href, window.location.href);
  }
  function urlOf(path) { return new URL(path, siteRoot()).href.replace(/index\.html$/, ""); }
  function normalize(href) { return href.replace(/index\.html$/, "").replace(/#.*$/, "").replace(/\/?$/, "/"); }

  function lessonById(id) { return Q.lessons.find(function (l) { return l.id === id; }); }
  function nextOf(id) {
    var i = Q.lessons.findIndex(function (l) { return l.id === id; });
    if (i < 0) return null;
    var current = Q.lessons[i];
    var next = Q.lessons[i + 1];
    if (!next || next.course !== current.course) {
      var course = Q.courses.find(function (c) { return c.id === current.course; });
      return course ? { title: "Knowledge check", url: course.quiz } : null;
    }
    return next;
  }
  function formatDate(iso) {
    try { return new Date(iso).toLocaleDateString("pt-BR"); } catch (e) { return ""; }
  }

  function renderComplete(state) {
    var box = document.querySelector(".quest-complete");
    if (!box) return;
    var id = box.getAttribute("data-lesson");
    var done = state.done[id];
    var next = nextOf(id);
    box.innerHTML = "";
    var stateEl = document.createElement("div");
    stateEl.className = "state" + (done ? " done" : "");
    stateEl.textContent = done ? "Lição concluída em " + formatDate(done) : "Rodou as duas versões e o check passou? Marque como concluída.";
    var actions = document.createElement("div");
    actions.className = "actions";
    var btn = document.createElement("button");
    btn.className = "quest-btn" + (done ? " secondary" : "");
    btn.textContent = done ? "Desmarcar" : "Marcar como concluída";
    btn.addEventListener("click", function () {
      var s = load();
      if (s.done[id]) delete s.done[id]; else s.done[id] = new Date().toISOString();
      save(s);
      renderAll();
    });
    actions.appendChild(btn);
    if (next) {
      var link = document.createElement("a");
      link.className = "quest-btn" + (done ? "" : " secondary");
      link.href = urlOf(next.url);
      link.textContent = "Continuar: " + next.title;
      actions.appendChild(link);
    }
    box.appendChild(stateEl);
    box.appendChild(actions);
  }

  function renderNav(state) {
    var byUrl = {};
    Q.lessons.forEach(function (l) { byUrl[normalize(urlOf(l.url))] = l; });
    document.querySelectorAll(".md-nav__link").forEach(function (a) {
      var href = a.getAttribute("href");
      if (!href) return;
      var lesson = byUrl[normalize(new URL(href, window.location.href).href)];
      if (lesson) a.classList.toggle("quest-done", !!state.done[lesson.id]);
    });
  }

  function renderCards(state) {
    document.querySelectorAll(".course-card[data-course]").forEach(function (card) {
      var cid = card.getAttribute("data-course");
      var lessons = Q.lessons.filter(function (l) { return l.course === cid; });
      var done = lessons.filter(function (l) { return state.done[l.id]; }).length;
      var quiz = state.quiz[cid];
      var badge = card.querySelector(".badge");
      var bar = card.querySelector(".course-progress span");
      if (bar) bar.style.width = lessons.length ? Math.round(100 * done / lessons.length) + "%" : "0%";
      if (!badge) return;
      badge.classList.remove("progress", "done");
      if (done === lessons.length && lessons.length > 0 && quiz && quiz.passed) {
        badge.textContent = "Concluído"; badge.classList.add("done");
      } else if (done > 0) {
        badge.textContent = done + " de " + lessons.length; badge.classList.add("progress");
      } else {
        badge.textContent = "Não iniciado";
      }
    });
  }

  function renderLessonLists(state) {
    document.querySelectorAll(".quest-lessons li[data-lesson]").forEach(function (li) {
      li.classList.toggle("done", !!state.done[li.getAttribute("data-lesson")]);
    });
  }

  function renderAll() {
    var state = load();
    renderComplete(state);
    renderNav(state);
    renderCards(state);
    renderLessonLists(state);
  }

  window.QUEST.progress = { load: load, save: save, renderAll: renderAll, urlOf: urlOf };
  if (typeof document$ !== "undefined") { document$.subscribe(renderAll); } else { document.addEventListener("DOMContentLoaded", renderAll); }
})();

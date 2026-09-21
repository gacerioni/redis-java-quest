/* Knowledge check: questions come from a JSON block in the page; result is stored with the progress. */
(function () {
  function render() {
    var box = document.querySelector(".quest-quiz[data-course]");
    var data = document.getElementById("quest-quiz-data");
    if (!box || !data || !window.QUEST || !window.QUEST.progress) return;
    var course = box.getAttribute("data-course");
    var questions;
    try { questions = JSON.parse(data.textContent); } catch (e) { box.textContent = "Quiz inválido."; return; }
    var P = window.QUEST.progress;
    box.innerHTML = "";
    questions.forEach(function (q, i) {
      var div = document.createElement("div");
      div.className = "q";
      div.setAttribute("data-index", i);
      var text = document.createElement("p");
      text.className = "text";
      text.textContent = (i + 1) + ". " + q.q;
      div.appendChild(text);
      q.options.forEach(function (opt, j) {
        var label = document.createElement("label");
        var input = document.createElement("input");
        input.type = "radio"; input.name = "q" + i; input.value = j;
        label.appendChild(input);
        label.appendChild(document.createTextNode(opt));
        div.appendChild(label);
      });
      var why = document.createElement("div");
      why.className = "why";
      why.textContent = q.why || "";
      div.appendChild(why);
      box.appendChild(div);
    });
    var btn = document.createElement("button");
    btn.className = "quest-btn";
    btn.textContent = "Conferir respostas";
    var error = document.createElement("div");
    error.className = "error";
    var result = document.createElement("div");
    result.className = "result";
    box.appendChild(btn); box.appendChild(error); box.appendChild(result);

    var previous = P.load().quiz[course];
    if (previous) result.textContent = "Última tentativa: " + previous.score + " de " + previous.total + (previous.passed ? ". Knowledge check concluído." : ".");

    btn.addEventListener("click", function () {
      error.textContent = "";
      var unanswered = questions.findIndex(function (_, i) { return !box.querySelector('input[name="q' + i + '"]:checked'); });
      if (unanswered >= 0) { error.textContent = "Responda a pergunta " + (unanswered + 1) + " antes de conferir."; return; }
      var score = 0;
      questions.forEach(function (q, i) {
        var chosen = parseInt(box.querySelector('input[name="q' + i + '"]:checked').value, 10);
        var div = box.querySelector('.q[data-index="' + i + '"]');
        div.classList.remove("right", "wrong");
        if (chosen === q.answer) { score++; div.classList.add("right"); } else { div.classList.add("wrong"); }
      });
      var passed = score >= Math.ceil(questions.length * 0.8);
      var state = P.load();
      state.quiz[course] = { score: score, total: questions.length, passed: passed, at: new Date().toISOString() };
      P.save(state);
      var courses = window.QUEST.courses || [];
      var current = courses.find(function (c) { return c.id === course; });
      var next = current && current.next ? courses.find(function (c) { return c.id === current.next; }) : null;
      result.innerHTML = "Você acertou " + score + " de " + questions.length + ". " +
        (passed ? "Knowledge check concluído." : "Revise as lições marcadas e tente de novo.") +
        (passed && next ? ' <a class="quest-btn" href="' + P.urlOf(next.url) + '">Próximo curso: ' + next.name + "</a>" : "");
      P.renderAll();
    });
  }
  if (typeof document$ !== "undefined") { document$.subscribe(render); } else { document.addEventListener("DOMContentLoaded", render); }
})();

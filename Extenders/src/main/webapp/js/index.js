
// エンドポイント
const ws = new WebSocket("ws://localhost:8080/Extenders/chat");


$(function() {

	const pElement = document.getElementById('result-text');
	pElement.textContent = "　";
	let prompt_box = document.getElementById("prompt-box");
	prompt_box.value = "　";

	$("#submit-button").click(function() {
		console.log("送信ボタンが押されました");
		let messageInput = document.getElementById("response-box");
		sendMessage("pr" + messageInput.value);
		messageInput.value = "";
		document.getElementById("submit-button").disabled = true;
		const pElement = document.getElementById('result-text');
		pElement.textContent = 'みんなの回答を待っています';
	});
});

// サーバからメッセージを受信したときの処理
ws.onmessage = function(event) {
  let pElement = document.getElementById('result-text');

  switch (event.data.substring(0, 2)) {
    case "tm":
      let prompt_box = document.getElementById("prompt-box");
      prompt_box.value = event.data.substring(2);
      break;
    case "gc":
      pElement.textContent = "GAME CLEAR";
      window.location.href = "clear.html";
      break;
    case "go":
      pElement.textContent = "GAME OVER";
      window.location.href = "gameover.html";
      break;
    default:
      break;
  }

  try {
    const msg = JSON.parse(event.data); // JSONパース
    if (msg.type === "waiting") { // まだ3人未満
      $("status").textContent = `あと ${msg.need} 人で開始`;
    } else if (msg.type === "assign") { // 役割割当
      myRole = msg.role;
      myCohort = msg.cohort;
      $("status").textContent = `割り当て: role=${myRole}, cohort=${myCohort}`;
      if (myRole === "PAIR") {
        $("pairView").classList.remove("hide");
        $("soloView").classList.add("hide");
      } else {
        $("soloView").classList.remove("hide");
        $("shareView").classList.remove("hide");
        $("pairView").classList.add("hide");
      }
    } else if (msg.type === "pairSync") {
      $("pairLog").textContent += msg.payload + "\n";
      $("shareLog").textContent += msg.payload + "\n";
    }
  } catch {
    if (event.data === "clear") {
      $("shareView").classList.add("hide");
      $("soloView").classList.add("hide");
      $("pairView").classList.add("hide");
      $("clear").classList.remove("hide");
    } else if (event.data === "bad") { // ← ev → event に修正
      $("shareView").classList.add("hide");
      $("soloView").classList.add("hide");
      $("pairView").classList.add("hide");
      $("bad").classList.remove("hide");
    }
  }
}; // ← ← ← ここで ws.onmessage のブロックが終わり！

// ボタンイベント
$("pairSend").onclick = () => {
  const payload = $("pairInput").value || "";
  ws.send(JSON.stringify({ type: "pairSync", payload }));
  $("pairView").classList.add("hide");
  $("shareView").classList.remove("hide");
};

$("soloSend").onclick = () => {
  const payload = $("soloInput").value || "";
  ws.send(JSON.stringify({ type: "soloSync", payload }));
};

// サーバー送信用
function sendMessage(value) {
  setTimeout(() => {
    ws.send(value);
  }, 300);
}

// 短縮関数（なければ追加）
function $(id) {
  return document.getElementById(id);
}

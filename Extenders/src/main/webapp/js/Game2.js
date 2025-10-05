
// エンドポイント
const ws = new WebSocket("wss://" + window.location.host + "/Extenders/Game2");
//const ws = new WebSocket("ws://localhost:8080/Extenders/Game2");
function $(id) {
  return document.getElementById(id);
}
let ans;
// サーバからメッセージを受信したときの処理
ws.onmessage = (ev) => {                                     // サーバからの受信時
		console.log(1);
		console.log(ev.data);
    try {
		
      const msg = JSON.parse(ev.data);                         // JSONパース
      if (msg.type === "count") {                              // 接続人数の更新
      $("status").textContent = `接続中: ${msg.total}人`;
      if(msg.total == 3){
		  
		  $("startWoView").classList.remove("hide");
	  }
      return;
    }else if (msg.type === "waiting") {                            // まだ3人未満
        $("status").textContent = `あと ${msg.need} 人で開始`;
      } else if (msg.type === "assign") {                      // 役割割当の通知
      $("create").classList.add("hide");
        console.log(2);
        myRole = msg.role;
        myCohort = msg.cohort;
        if (myRole === "PAIR") {                               // 役割に応じて表示切替
          $("pairView").classList.remove("hide");
          $("soloView").classList.add("hide");
          if (msg.word) { $("pairWord").textContent = `お題: ${msg.word}`; }
          console.log(3);
          $("answer").textContent += "「"+msg.ans+"」が答えでした";
          ans = msg.word;
        } else {
			$("answer").textContent += "「"+msg.ans+"」が答えでした";
          $("soloView").classList.remove("hide");
          $("shareView").classList.remove("hide");
          $("pairView").classList.add("hide");
          console.log(4);
        }
      } else if (msg.type === "pairSync") {                    // PAIR向け受信
        $("shareLog").textContent += msg.payload + "\n";
        console.log(5);
      }
    } catch {
		if(ev.data === "haide"){
        $("startView").classList.add("hide");
      $("startWoView").classList.add("hide"); 
      $("create").classList.remove("hide"); 
      }else if(ev.data === "clear"){
		  
		  $("answer").classList.remove("hide");
        $("shareView").classList.add("hide");
        $("soloView").classList.add("hide");
        $("pairView").classList.add("hide");
        $("clear").classList.remove("hide");
      }else if(ev.data === "bad"){
		  $("answer").classList.remove("hide");
        $("shareView").classList.add("hide");
        $("soloView").classList.add("hide");
        $("pairView").classList.add("hide");
        $("bad").classList.remove("hide");
      }
      
    }
  };

  $("pairSend").onclick = () => {                              // PAIR送信ボタン
    const payload = $("pairInput").value || "";
    ws.send(JSON.stringify({type:"pairSync", payload}));       // type=pairSync で送信
    $("pairView").classList.add("hide");
    $("shareView").classList.remove("hide");
  };

  $("soloSend").onclick = () => {                              // SOLO送信ボタン
    const payload = $("soloInput").value || "";
    ws.send(JSON.stringify({type:"soloSync", payload}));       // type=soloSync で送信
    
  };
  
 $("wo").onclick = () => {
	 
   ws.send("wo"); 

 };

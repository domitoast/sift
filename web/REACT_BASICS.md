# React 基礎（這個專案會用到的全部）

> 這份不是 React 教學，是「這個專案用到哪些、各自解決什麼問題」。
>
> 讀完大約 20 分鐘。之後寫程式卡住時回來查。

---

## 0. 一句話總結

> **你只管資料，畫面自己跟上。**

其他所有東西都是為了這句話服務的。

---

## 1. 元件

**元件就是一個回傳畫面的函式。**

```jsx
function Hello() {
  return <h1>你好</h1>
}
```

用的時候像一個 HTML 標籤：

```jsx
<Hello />
```

**規則：元件的名字第一個字母必須大寫。** 小寫的 React 會當成普通的 HTML 標籤。

```jsx
<Hello />    ← 我寫的元件
<div />      ← 瀏覽器原生的
```

---

## 2. JSX

**在 JavaScript 裡直接寫出畫面的樣子。**

```jsx
return <button className="primary">送出</button>
```

瀏覽器看不懂這個，所以需要 build（Vite 幫你翻譯）。

### 三個和 HTML 不同的地方

| HTML | JSX | 為什麼 |
|---|---|---|
| `class="x"` | `className="x"` | `class` 在 JavaScript 是保留字 |
| `onclick=` | `onClick=` | JSX 一律用駝峰式 |
| `<br>` | `<br />` | 沒有內容的標籤一定要自己關 |

### `{ }` 是「這裡面是 JavaScript」

```jsx
const name = '阿赫'

<div>你好，{name}</div>              // 你好，阿赫
<div>1 + 1 = {1 + 1}</div>          // 1 + 1 = 2
<img src={imageUrl} />              // 變數
<button onClick={handleClick} />    // 函式
<div className="fixed" />           // 沒有大括號 = 純字串
```

### 只能回傳一個東西

```jsx
// ❌ 錯：兩個並排的元素
return (
  <h1>標題</h1>
  <p>內文</p>
)

// ✅ 包起來
return (
  <div>
    <h1>標題</h1>
    <p>內文</p>
  </div>
)

// ✅ 不想多一層 div 的話用空標籤
return (
  <>
    <h1>標題</h1>
    <p>內文</p>
  </>
)
```

---

## 3. useState — 會讓畫面更新的變數

```jsx
const [count, setCount] = useState(0)
//     ↑ 現在的值   ↑ 改它的唯一方法    ↑ 初始值
```

### ⚠️ 最重要的一條規則

**只有透過 `setXxx` 改，畫面才會更新。**

```jsx
let count = 0
<button onClick={() => count++}>     // 數字真的變了，但畫面不動
```

因為 React 是靠「`setCount` 被呼叫」才知道該重畫。

### 狀態改變之後發生什麼

```
setCount(1)
   ↓
React：「狀態變了」
   ↓
把整個元件函式再執行一次
   ↓
拿到新的畫面描述
   ↓
跟舊的比對，只更新真正變的地方
```

**所以元件是一個會被重複執行的函式。** 這句話是理解 React 的關鍵。

### 什麼該是狀態

> **畫面會因為它而改變的，才是狀態。**

```jsx
const [sources, setSources] = useState([])   // ✅ 會變，畫面跟著變
const count = sources.length                  // ❌ 從狀態算出來的，不要另外存
```

**能算出來的就不要另存** —— 存兩份就會有不一致的一天。

### 常見錯誤：連續呼叫兩次

```jsx
setCount(count + 1)
setCount(count + 1)    // 結果只加了 1，不是 2
```

因為這一輪的 `count` 還是舊值。要連續改就用函式版：

```jsx
setCount(c => c + 1)
setCount(c => c + 1)    // 加 2
```

---

## 4. useEffect — 「不要每次渲染都跑」

### 要解決什麼問題

```jsx
function App() {
  const [data, setData] = useState([])

  fetch('/api/v1/sources')     // ❌ 無限迴圈
    .then(r => r.json())
    .then(setData)             // setData → 重新執行 → 又 fetch → ...
}
```

因為元件會被重複執行，直接寫在裡面的東西每次都會跑。

### 寫法

```jsx
useEffect(() => {
  // 要做的事
}, [依賴陣列])
```

| 依賴陣列 | 什麼時候執行 |
|---|---|
| `[]` | **只有第一次**，之後都不再跑 |
| `[token]` | 第一次 + **每次 `token` 變的時候** |
| 省略不寫 | **每次渲染都跑** —— 幾乎永遠是錯的 |

### 典型用法

```jsx
useEffect(() => {
  if (!token) return          // 還沒登入就不要打 API

  fetch('/api/v1/sources', {
    headers: { Authorization: 'Bearer ' + token },
  })
    .then(res => res.json())
    .then(setSources)

}, [token])
```

**讀法**：「`token` 變了的時候，去拿來源清單。」

---

## 5. 清單：`.map()` 與 `key`

### `.map()` 把陣列變成一堆元素

```jsx
{sources.map(s => (
  <div key={s.id}>{s.name}</div>
))}
```

`.map()` 是 JavaScript 原本就有的：把陣列的每一項轉換成別的東西，產生新陣列。
React 看到一個「元素的陣列」就會全部畫出來。

### `key` 是給 React 認人用的

刪掉清單中間一筆的時候：

| | React 怎麼想 |
|---|---|
| **沒有 key** | 只能用位置比對 → 以為「第 2 個的內容變了」→ 只改文字，不重建 |
| **有 key** | 「key=2 不見了」→ 把那一整塊刪掉 |

沒有 key 的話，那一格的輸入框內容、捲動位置、動畫狀態都會留在原地 —— 變成別人的。

### 什麼時候需要

```jsx
<h2>訂閱來源</h2>                       // ❌ 寫死的，不用
{sources.map(s => <div key={s.id}>)}    // ✅ 數量會變的一組兄弟
```

### ⚠️ 不要用 index

```jsx
{sources.map((s, i) => <div key={i}>)}   // ❌ index 也是位置，等於沒寫
```

**要用跟著那筆資料走的東西，通常是 `id`。**

> `key` 就是「身分」。位置不是身分。
> （跟後端用 content hash 而不是 URL 判斷「同一篇文章」是同一個道理。）

---

## 6. 條件顯示

### 用 `if` 直接回傳不同畫面

```jsx
if (!token) {
  return <LoginPage />
}

return <MainPage />
```

### 用 `&&` 顯示或不顯示一小塊

```jsx
{error && <div className="error">{error}</div>}
```

**讀法**：「`error` 有值的話，才顯示這個 div。」

> ⚠️ 陷阱：`{count && <div>}` 當 `count` 是 `0` 時會在畫面上印出 `0`。
> 數字要寫成 `{count > 0 && <div>}`。

### 用三元運算子二選一

```jsx
{loading ? <div>載入中…</div> : <List items={items} />}
```

---

## 7. 表單：受控元件

```jsx
const [email, setEmail] = useState('')

<input
  value={email}                                  // 顯示的內容 = 狀態
  onChange={(e) => setEmail(e.target.value)}     // 打字 → 更新狀態
/>
```

繞了一圈，但好處是：**`email` 這個變數永遠等於畫面上看到的東西。**
你不用去問 DOM「現在框裡是什麼」。

`e.target.value` 裡的 `e` 是事件物件，`target` 是觸發的那個元素。

---

## 8. 事件

```jsx
<button onClick={handleClick}>      // ✅ 把函式交出去
<button onClick={handleClick()}>    // ❌ 現在就執行，把回傳值交出去
<button onClick={() => doSomething(id)}>   // ✅ 要傳參數就包一層
```

**跟 Java 的 `job::succeed` 是同一件事：傳「動作本身」，不是「執行結果」。**

---

## 9. Hooks 的規則

`useState`、`useEffect` 這些 `use` 開頭的東西叫 **hook**。

**兩條規則，違反了 React 會直接報錯：**

1. **只能在元件函式的最上層呼叫** —— 不能放在 `if`、迴圈、或巢狀函式裡
2. **只能在元件裡呼叫** —— 不能在普通函式裡用

```jsx
// ❌
if (token) {
  const [x, setX] = useState(0)
}

// ✅
const [x, setX] = useState(0)
if (token) { ... }
```

**原因**：React 靠「呼叫的順序」來記住哪個狀態是哪個。順序一變就全亂了。

---

## 10. 常見錯誤速查

| 症狀 | 原因 |
|---|---|
| 畫面不更新 | 直接改變數了，沒用 `setXxx` |
| 無限迴圈、瀏覽器卡住 | `fetch` 沒包在 `useEffect` 裡，或依賴陣列寫錯 |
| Console 一直警告 `key` | `.map()` 忘了加 `key` |
| 連續 `setCount(count+1)` 只加一次 | 要用 `setCount(c => c + 1)` |
| 畫面莫名其妙印出一個 `0` | `{count && ...}` 要改成 `{count > 0 && ...}` |
| `Rendered fewer hooks than expected` | hook 被放在 `if` 裡面了 |

---

## 11. 對照表：vanilla vs React

| 要做的事 | 你的 `index.html` | React |
|---|---|---|
| 改資料後更新畫面 | 手動 `textContent = ...` | `setXxx`，自動 |
| 畫一串清單 | `for` + `createElement` + `append` | `.map()` |
| 重畫前要先清空 | `innerHTML = ''` | 不用，React 自己處理 |
| 換頁 | `style.display = 'none'` | `if (...) return <A />` |
| 取得輸入框的值 | `$('email').value` | 狀態 `email` |
| 載入時拿資料 | 在 `login()` 裡呼叫 | `useEffect` |

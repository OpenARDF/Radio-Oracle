async function loadResults() {
  const response = await fetch("data/results.json", { cache: "no-store" });
  if (!response.ok) {
    throw new Error(`Unable to load sample results: ${response.status}`);
  }
  return response.json();
}

function renderSummary(data) {
  document.getElementById("race-name").textContent = data.race.name;
  document.getElementById("category-count").textContent = data.categories.length.toString();
  document.getElementById("published-at").textContent = data.publishedAt;
}

function renderResults(data, query = "") {
  const normalizedQuery = query.trim().toLowerCase();
  const root = document.getElementById("results");
  root.innerHTML = "";

  data.categories.forEach((category) => {
    const rows = category.results.filter((result) => {
      const text = `${result.place} ${result.name} ${result.club} ${result.status} ${result.time}`.toLowerCase();
      return text.includes(normalizedQuery);
    });
    if (rows.length === 0) return;

    const section = document.createElement("section");
    section.className = "category";
    section.innerHTML = `
      <h3>${escapeHtml(category.name)}</h3>
      <table>
        <thead>
          <tr>
            <th class="number">Place</th>
            <th>Name</th>
            <th>Club</th>
            <th>Status</th>
            <th class="number">Points</th>
            <th>Time</th>
          </tr>
        </thead>
        <tbody>
          ${rows.map((result) => `
            <tr>
              <td class="number">${escapeHtml(result.place)}</td>
              <td>${escapeHtml(result.name)}</td>
              <td>${escapeHtml(result.club)}</td>
              <td>${escapeHtml(result.status)}</td>
              <td class="number">${escapeHtml(String(result.points))}</td>
              <td>${escapeHtml(result.time)}</td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    `;
    root.appendChild(section);
  });
}

function escapeHtml(value) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

loadResults()
  .then((data) => {
    renderSummary(data);
    renderResults(data);
    document.getElementById("search").addEventListener("input", (inputChange) => {
      renderResults(data, inputChange.target.value);
    });
  })
  .catch((error) => {
    document.getElementById("results").textContent = error.message;
  });

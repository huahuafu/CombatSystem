import React from "react";

export default function Timeline({ items, emptyText = "暂无时序" }) {
  const list = items?.length ? items : [emptyText];
  return (
    <div className="x-timeline">
      {list.map((x, i) => (
        <div className="x-step" key={i}>
          <div className="x-step-no">{i + 1}</div>
          <div>{x}</div>
        </div>
      ))}
    </div>
  );
}

import * as maplibregl from "maplibre-gl";
import type { Map as MapInstance, Marker } from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
import { useEffect, useRef, useState } from "react";
import type { NavigatorItem } from "./navigator.types";
import { initials } from "../employees/PeoplePage";
export function NavigatorMap({
  items,
  selected,
  onSelect,
}: {
  items: NavigatorItem[];
  selected?: string;
  onSelect: (id: string) => void;
}) {
  const host = useRef<HTMLDivElement>(null),
    map = useRef<MapInstance | null>(null),
    markers = useRef(new Map<string, Marker>()),
    [failed, setFailed] = useState(false);
  useEffect(() => {
    if (!host.current || map.current) return;
    const container = host.current;
    let resizeFrame: number | undefined;
    let observer: ResizeObserver | undefined;
    let instance: MapInstance | undefined;
    try {
      const mapInstance = new maplibregl.Map({
        container,
        style: "https://tiles.openfreemap.org/styles/liberty",
        center: [80.3319, 26.4499],
        zoom: 12,
        attributionControl: { compact: true },
      });
      instance = mapInstance;
      map.current = mapInstance;
      mapInstance.addControl(
        new maplibregl.NavigationControl({ showCompass: false }),
        "top-right",
      );
      mapInstance.once("load", () => mapInstance.resize());
      observer = new ResizeObserver(() => mapInstance.resize());
      observer.observe(container);
      resizeFrame = requestAnimationFrame(() => mapInstance.resize());
    } catch (error) {
      console.error("Navigator map initialization failed.", error);
      setFailed(true);
    }
    return () => {
      if (resizeFrame !== undefined) cancelAnimationFrame(resizeFrame);
      observer?.disconnect();
      markers.current.forEach((m) => m.remove());
      markers.current.clear();
      if (map.current === instance) map.current = null;
      instance?.remove();
    };
  }, []);
  useEffect(() => {
    const visible = new Set<string>();
    for (const item of items) {
      if (
        item.latitude == null ||
        item.longitude == null ||
        item.state === "OFF_DUTY" ||
        item.state === "UNPAIRED"
      )
        continue;
      visible.add(item.employeeRef);
      let marker = markers.current.get(item.employeeRef);
      if (!marker) {
        const el = document.createElement("button");
        el.className = "navigator-marker";
        el.setAttribute(
          "aria-label",
          `Show ${item.employeeName ?? "employee"}`,
        );
        el.onclick = () => onSelect(item.employeeRef);
        el.textContent = initials(item.employeeName ?? "Employee");
        const created = new maplibregl.Marker({ element: el })
          .setLngLat([item.longitude, item.latitude])
          .addTo(map.current!);
        markers.current.set(item.employeeRef, created);
        marker = created;
      }
      marker.setLngLat([item.longitude, item.latitude]);
      const el = marker.getElement();
      el.dataset.state = item.state;
      el.classList.toggle("selected", selected === item.employeeRef);
    }
    for (const [id, marker] of markers.current)
      if (!visible.has(id)) {
        marker.remove();
        markers.current.delete(id);
      }
  }, [items, onSelect, selected]);
  return (
    <div className="navigator-map-wrap">
      {failed && (
        <div className="navigator-map-fallback">
          <strong>Map temporarily unavailable</strong>
          <span>Live roster remains available.</span>
        </div>
      )}
      <div
        ref={host}
        className="navigator-map"
        aria-label="Live employee location map"
      />
    </div>
  );
}

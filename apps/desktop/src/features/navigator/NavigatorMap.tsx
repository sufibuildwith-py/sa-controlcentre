import * as maplibregl from "maplibre-gl";
import type { Map as MapInstance, Marker } from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
import { useEffect, useRef, useState } from "react";
import { initials } from "../employees/PeoplePage";
import {
  isValidCoordinate,
  resolveInitialMapCenter,
  type NavigatorItem,
} from "./navigator.types";

interface FocusedTeam {
  teamName: string;
  employeeRefs: string[];
}

export function NavigatorMap({
  items,
  selected,
  focusedTeam,
  onSelect,
}: {
  items: NavigatorItem[];
  selected?: string;
  focusedTeam?: FocusedTeam | null;
  onSelect: (id: string) => void;
}) {
  const host = useRef<HTMLDivElement>(null);
  const map = useRef<MapInstance | null>(null);
  const markers = useRef(new Map<string, Marker>());
  const [failed, setFailed] = useState(false);

  const initialCenterResolved = useRef(false);
  const userInteracted = useRef(false);

  // Initialize map once
  useEffect(() => {
    if (!host.current || map.current) return;
    const container = host.current;
    let resizeFrame: number | undefined;
    let observer: ResizeObserver | undefined;
    let instance: MapInstance | undefined;

    try {
      const initialCenter = resolveInitialMapCenter(items);
      const hasCoordsOnMount = items.some((i) =>
        isValidCoordinate(i.latitude, i.longitude),
      );
      if (hasCoordsOnMount) {
        initialCenterResolved.current = true;
      }

      const mapInstance = new maplibregl.Map({
        container,
        style: "https://tiles.openfreemap.org/styles/liberty",
        center: initialCenter,
        zoom: 12,
        attributionControl: { compact: true },
      });

      instance = mapInstance;
      map.current = mapInstance;

      mapInstance.addControl(
        new maplibregl.NavigationControl({ showCompass: false }),
        "top-right",
      );

      // Track intentional user panning/zooming to prevent resetting viewport
      mapInstance.on("dragstart", () => {
        userInteracted.current = true;
      });
      mapInstance.on("zoomstart", (e) => {
        if (e.originalEvent) userInteracted.current = true;
      });

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
  }, []); // Run once on mount

  // Apply dynamic initial center once if location data arrives asynchronously after mount
  useEffect(() => {
    if (!map.current || initialCenterResolved.current || userInteracted.current)
      return;
    const hasCoords = items.some((i) =>
      isValidCoordinate(i.latitude, i.longitude),
    );
    if (hasCoords) {
      const dynamicCenter = resolveInitialMapCenter(items);
      map.current.jumpTo({ center: dynamicCenter, zoom: 12 });
      initialCenterResolved.current = true;
    }
  }, [items]);

  // Sync markers with items
  useEffect(() => {
    if (!map.current) return;
    const visible = new Set<string>();

    for (const item of items) {
      if (
        !isValidCoordinate(item.latitude, item.longitude) ||
        item.state === "OFF_DUTY" ||
        item.state === "UNPAIRED"
      ) {
        continue;
      }

      visible.add(item.employeeRef);
      let marker = markers.current.get(item.employeeRef);

      if (!marker) {
        const el = document.createElement("button");
        el.className = "navigator-marker";
        el.setAttribute(
          "aria-label",
          `Show ${item.employeeName ?? "employee"} location`,
        );
        el.title = `${item.employeeName ?? "Employee"} · ${item.state}`;
        el.onclick = (e) => {
          e.stopPropagation();
          onSelect(item.employeeRef);
        };
        el.textContent = initials(item.employeeName ?? "Employee");

        const created = new maplibregl.Marker({ element: el })
          .setLngLat([item.longitude!, item.latitude!])
          .addTo(map.current);

        markers.current.set(item.employeeRef, created);
        marker = created;
      }

      marker.setLngLat([item.longitude!, item.latitude!]);
      const el = marker.getElement();
      el.dataset.state = item.state;
      el.classList.toggle("selected", selected === item.employeeRef);

      const isInFocusedTeam =
        !!focusedTeam && focusedTeam.employeeRefs.includes(item.employeeRef);
      el.classList.toggle("in-focused-team", isInFocusedTeam);
    }

    for (const [id, marker] of markers.current) {
      if (!visible.has(id)) {
        marker.remove();
        markers.current.delete(id);
      }
    }
  }, [items, onSelect, selected, focusedTeam]);

  // Handle employee selection focus
  useEffect(() => {
    if (!map.current || !selected) return;
    const target = items.find((i) => i.employeeRef === selected);
    if (target && isValidCoordinate(target.latitude, target.longitude)) {
      map.current.flyTo({
        center: [target.longitude!, target.latitude!],
        zoom: Math.max(map.current.getZoom(), 14),
        speed: 1.2,
        essential: true,
      });
    }
  }, [selected, items]);

  // Handle team focus bounding box
  useEffect(() => {
    if (!map.current || !focusedTeam || !focusedTeam.employeeRefs.length) return;

    const teamMembers = items.filter(
      (i) =>
        focusedTeam.employeeRefs.includes(i.employeeRef) &&
        isValidCoordinate(i.latitude, i.longitude),
    );

    if (!teamMembers.length) return;

    if (teamMembers.length === 1) {
      map.current.flyTo({
        center: [teamMembers[0].longitude!, teamMembers[0].latitude!],
        zoom: 14,
        speed: 1.2,
        essential: true,
      });
      return;
    }

    const bounds = new maplibregl.LngLatBounds();
    for (const m of teamMembers) {
      bounds.extend([m.longitude!, m.latitude!]);
    }

    map.current.fitBounds(bounds, {
      padding: 60,
      maxZoom: 15,
      duration: 1000,
    });
  }, [focusedTeam, items]);

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

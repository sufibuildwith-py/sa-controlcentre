import { useState, useRef, type MouseEvent, type ReactNode } from "react";
import { motion, useReducedMotion, type HTMLMotionProps } from "motion/react";

interface CardSpotlightProps extends HTMLMotionProps<"div"> {
  children: ReactNode;
  className?: string;
  spotlightColor?: string;
  spotlightRadius?: number;
  interactive?: boolean;
}

export function CardSpotlight({
  children,
  className = "",
  spotlightColor,
  spotlightRadius = 320,
  interactive = false,
  ...props
}: CardSpotlightProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [position, setPosition] = useState({ x: -1000, y: -1000 });
  const [isHovered, setIsHovered] = useState(false);
  const reducedMotion = useReducedMotion();

  const handleMouseMove = (e: MouseEvent<HTMLDivElement>) => {
    if (reducedMotion || !containerRef.current) return;
    const rect = containerRef.current.getBoundingClientRect();
    setPosition({
      x: e.clientX - rect.left,
      y: e.clientY - rect.top,
    });
  };

  const handleMouseEnter = () => {
    if (!reducedMotion) setIsHovered(true);
  };

  const handleMouseLeave = () => {
    setIsHovered(false);
    setPosition({ x: -1000, y: -1000 });
  };

  const defaultColor = "rgba(214, 87, 66, 0.06)";
  const color = spotlightColor ?? defaultColor;

  return (
    <motion.div
      ref={containerRef}
      onMouseMove={handleMouseMove}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
      className={`command-spotlight-card ${interactive ? "interactive" : ""} ${className}`}
      whileHover={interactive && !reducedMotion ? { y: -2 } : undefined}
      transition={{ duration: 0.16, ease: "easeOut" }}
      {...props}
    >
      {!reducedMotion && (
        <div
          className="command-spotlight-layer"
          style={{
            opacity: isHovered ? 1 : 0,
            background: `radial-gradient(${spotlightRadius}px circle at ${position.x}px ${position.y}px, ${color}, transparent 80%)`,
          }}
          aria-hidden="true"
        />
      )}
      <div className="command-spotlight-content">{children}</div>
    </motion.div>
  );
}

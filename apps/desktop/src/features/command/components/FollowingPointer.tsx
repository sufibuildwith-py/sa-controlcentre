import { useState, useRef, type MouseEvent, type ReactNode } from "react";
import { motion, AnimatePresence, useReducedMotion } from "motion/react";

interface FollowingPointerProps {
  children: ReactNode;
  content?: ReactNode;
  className?: string;
  disabled?: boolean;
}

export function FollowingPointer({
  children,
  content,
  className = "",
  disabled = false,
}: FollowingPointerProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [position, setPosition] = useState({ x: 0, y: 0 });
  const [isHovered, setIsHovered] = useState(false);
  const reducedMotion = useReducedMotion();

  const handleMouseMove = (e: MouseEvent<HTMLDivElement>) => {
    if (disabled || reducedMotion || !containerRef.current) return;
    const rect = containerRef.current.getBoundingClientRect();
    setPosition({
      x: e.clientX - rect.left + 14,
      y: e.clientY - rect.top + 14,
    });
  };

  const handleMouseEnter = () => {
    if (!disabled && !reducedMotion && content) {
      setIsHovered(true);
    }
  };

  const handleMouseLeave = () => {
    setIsHovered(false);
  };

  return (
    <div
      ref={containerRef}
      onMouseMove={handleMouseMove}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
      className={`relative ${className}`}
      style={{ position: "relative" }}
    >
      {children}
      <AnimatePresence>
        {isHovered && content && !reducedMotion && (
          <motion.div
            initial={{ opacity: 0, scale: 0.92 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.92 }}
            transition={{ duration: 0.14, ease: "easeOut" }}
            style={{
              position: "absolute",
              top: 0,
              left: 0,
              x: position.x,
              y: position.y,
              pointerEvents: "none",
              zIndex: 50,
            }}
          >
            <div className="command-tooltip-content pointer-events-none">
              {content}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

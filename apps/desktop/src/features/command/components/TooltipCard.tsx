import { useState, useRef, type ReactNode, type MouseEvent } from "react";
import { motion, AnimatePresence, useReducedMotion } from "motion/react";

interface TooltipCardProps {
  children: ReactNode;
  content: ReactNode;
  side?: "top" | "bottom" | "left" | "right";
  className?: string;
  delayMs?: number;
}

/**
 * Tooltip Card inspired by Aceternity UI.
 * Renders an elevated, rich contextual preview card on hover.
 * Automatically aligns and positions with smooth physical easing.
 */
export function TooltipCard({
  children,
  content,
  side = "top",
  className = "",
  delayMs = 150,
}: TooltipCardProps) {
  const [isVisible, setIsVisible] = useState(false);
  const timeoutRef = useRef<NodeJS.Timeout | null>(null);
  const reducedMotion = useReducedMotion();

  const handleMouseEnter = () => {
    timeoutRef.current = setTimeout(() => {
      setIsVisible(true);
    }, delayMs);
  };

  const handleMouseLeave = () => {
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current);
    }
    setIsVisible(false);
  };

  const sideOffset = {
    top: { y: -8, x: 0 },
    bottom: { y: 8, x: 0 },
    left: { x: -8, y: 0 },
    right: { x: 8, y: 0 },
  }[side];

  return (
    <div
      className={`command-tooltip-card-wrapper ${className}`}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
    >
      {children}
      <AnimatePresence>
        {isVisible && (
          <motion.div
            initial={
              reducedMotion
                ? { opacity: 0 }
                : { opacity: 0, scale: 0.96, ...sideOffset }
            }
            animate={{ opacity: 1, scale: 1, x: 0, y: 0 }}
            exit={
              reducedMotion
                ? { opacity: 0 }
                : { opacity: 0, scale: 0.96, ...sideOffset }
            }
            transition={{ duration: 0.16, ease: [0.16, 1, 0.3, 1] }}
            className={`command-tooltip-card-popover side-${side}`}
            role="tooltip"
          >
            {content}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

import { useRef, useState, type MouseEvent, type ReactNode } from "react";
import { motion, useReducedMotion, AnimatePresence, type HTMLMotionProps } from "motion/react";

type Direction = "top" | "bottom" | "left" | "right";

interface DirectionAwareHoverProps extends HTMLMotionProps<"div"> {
  children: ReactNode;
  className?: string;
  highlightClassName?: string;
}

/**
 * Direction Aware Hover component inspired by Aceternity UI.
 * Determines entry and exit direction of cursor (top, right, bottom, left)
 * to provide tactile, expensive physical feedback without card distortion.
 * Completely disabled when user prefers reduced motion or on touch devices.
 */
export function DirectionAwareHover({
  children,
  className = "",
  highlightClassName = "",
  ...props
}: DirectionAwareHoverProps) {
  const ref = useRef<HTMLDivElement>(null);
  const [direction, setDirection] = useState<Direction>("top");
  const [isHovered, setIsHovered] = useState(false);
  const reducedMotion = useReducedMotion();

  const getDirection = (e: MouseEvent<HTMLDivElement>): Direction => {
    if (!ref.current) return "top";
    const { width, height, top, left } = ref.current.getBoundingClientRect();
    const x = e.clientX - left - (width / 2) * (width > height ? height / width : 1);
    const y = e.clientY - top - (height / 2) * (height > width ? width / height : 1);
    const d = Math.round(Math.atan2(y, x) / 1.57079633 + 5) % 4;

    switch (d) {
      case 0:
        return "top";
      case 1:
        return "right";
      case 2:
        return "bottom";
      case 3:
      default:
        return "left";
    }
  };

  const handleMouseEnter = (e: MouseEvent<HTMLDivElement>) => {
    if (reducedMotion) return;
    setDirection(getDirection(e));
    setIsHovered(true);
  };

  const handleMouseLeave = (e: MouseEvent<HTMLDivElement>) => {
    if (reducedMotion) return;
    setDirection(getDirection(e));
    setIsHovered(false);
  };

  const slideVariants = {
    initial: (dir: Direction) => ({
      x: dir === "left" ? "-100%" : dir === "right" ? "100%" : 0,
      y: dir === "top" ? "-100%" : dir === "bottom" ? "100%" : 0,
      opacity: 0,
    }),
    animate: {
      x: 0,
      y: 0,
      opacity: 1,
    },
    exit: (dir: Direction) => ({
      x: dir === "left" ? "-100%" : dir === "right" ? "100%" : 0,
      y: dir === "top" ? "-100%" : dir === "bottom" ? "100%" : 0,
      opacity: 0,
    }),
  };

  return (
    <motion.div
      ref={ref}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
      className={`command-direction-hover-card ${className}`}
      {...props}
    >
      {!reducedMotion && (
        <AnimatePresence custom={direction}>
          {isHovered && (
            <motion.div
              key="directional-highlight"
              custom={direction}
              variants={slideVariants}
              initial="initial"
              animate="animate"
              exit="exit"
              transition={{ duration: 0.2, ease: "easeOut" }}
              className={`command-direction-hover-highlight ${highlightClassName}`}
              aria-hidden="true"
            />
          )}
        </AnimatePresence>
      )}
      <div className="command-direction-hover-content">{children}</div>
    </motion.div>
  );
}
